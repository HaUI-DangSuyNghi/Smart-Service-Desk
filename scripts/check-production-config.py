"""Validate a resolved production Compose configuration without printing secrets."""

import argparse
import copy
import ipaddress
import json
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit


def validate(config, realm):
    errors = []
    services = config.get("services", {})
    expected = ("postgres", "keycloak", "platform", "gateway", "frontend", "intelligence")
    for name in expected:
        service = services.get(name, {})
        if not re.fullmatch(r"[^\s]+@sha256:[a-f0-9]{64}", service.get("image", "")):
            errors.append(name + ": require an immutable image digest")
        if service.get("build"):
            errors.append(name + ": runtime deployment must not build images")
        for port in service.get("ports", []):
            if port.get("host_ip") not in ("127.0.0.1", "::1"):
                errors.append(name + ": published ports must be loopback-only behind the TLS proxy")
    platform = services.get("platform", {}).get("environment", {})
    identity = services.get("keycloak", {}).get("environment", {})
    database = services.get("postgres", {}).get("environment", {})
    intelligence = services.get("intelligence", {}).get("environment", {})
    command = services.get("keycloak", {}).get("command", [])
    if not command or command[0] != "start" or any("start-dev" in item for item in command):
        errors.append("keycloak: production start is required")
    for name, value in (("application", platform.get("APP_BASE_URL", "")),
                        ("identity", identity.get("KC_HOSTNAME", ""))):
        url = urlsplit(value)
        if not (url.scheme == "https" and url.hostname and not url.username and not url.password
                and not url.query and not url.fragment and not url.path):
            errors.append(name + ": require a public HTTPS origin without credentials or a path")
    if platform.get("JWT_ISSUER") != identity.get("KC_HOSTNAME", "").rstrip("/") + "/realms/dx-lab":
        errors.append("platform: JWT issuer must match the public identity origin")
    if platform.get("SPRING_PROFILES_ACTIVE") != "production":
        errors.append("platform: require the production Spring profile")
    if str(platform.get("DX_ALLOW_UNSCANNED_UPLOAD", "")).lower() != "false":
        errors.append("platform: unscanned uploads must remain disabled")
    if identity.get("KC_PROXY_HEADERS") != "xforwarded":
        errors.append("keycloak: require explicit reverse-proxy header configuration")
    try:
        proxies = identity.get("KC_PROXY_TRUSTED_ADDRESSES", "").split(",")
        if not proxies or any(ipaddress.ip_network(item.strip(), strict=False).prefixlen == 0 for item in proxies):
            raise ValueError("unrestricted proxy")
    except ValueError:
        errors.append("keycloak: supply explicit trusted proxy IPs/CIDRs, never a catch-all")
    if database.get("POSTGRES_USER") != "bootstrap" or identity.get("KC_DB_USERNAME") != "identity":
        errors.append("database: require separate bootstrap, application and identity roles")
    credentials = [database.get("POSTGRES_PASSWORD", ""), platform.get("POSTGRES_PASSWORD", ""),
                   identity.get("KC_DB_PASSWORD", ""), identity.get("KC_BOOTSTRAP_ADMIN_PASSWORD", ""),
                   platform.get("DX_DIRECTORY_CLIENT_SECRET", ""), platform.get("DX_BACKGROUND_CLIENT_SECRET", "")]
    if any(len(value) < 32 for value in credentials) or len(set(credentials)) != len(credentials):
        errors.append("secrets: require distinct secrets of at least 32 characters for all six authorities")
    token = intelligence.get("INTELLIGENCE_TRIAGE_TOKEN", "")
    if len(token) < 32 or token in credentials or platform.get("INTELLIGENCE_TRIAGE_TOKEN") != token:
        errors.append("intelligence: require a distinct service secret matching the platform")
    if platform.get("INTELLIGENCE_TRIAGE_BASE_URL") != "http://intelligence:8000":
        errors.append("intelligence: require the internal service endpoint")
    if database.get("APP_DATABASE_PASSWORD") != platform.get("POSTGRES_PASSWORD") or database.get("IDENTITY_DATABASE_PASSWORD") != identity.get("KC_DB_PASSWORD"):
        errors.append("database: initialization credentials must match the runtime roles")
    if realm.get("realm") != "dx-lab" or not realm.get("sslRequired") == "external":
        errors.append("realm: require dx-lab and external HTTPS")
    for user in realm.get("users", []):
        if not user.get("serviceAccountClientId"):
            errors.append("realm: provision human identities separately, never import demo users")
    clients = {client.get("clientId"): client for client in realm.get("clients", [])}
    directory = clients.get("dx-directory", {})
    if "kc-admin-api" not in directory.get("defaultClientScopes", []) + directory.get("optionalClientScopes", []):
        errors.append("realm: dx-directory requires the kc-admin-api client scope")
    directory_accounts = [user for user in realm.get("users", []) if user.get("serviceAccountClientId") == "dx-directory"]
    directory_roles = set(directory_accounts[0].get("clientRoles", {}).get("realm-management", [])) if len(directory_accounts) == 1 else set()
    if not {"view-users", "query-users", "query-groups", "view-clients"} <= directory_roles:
        errors.append("realm: dx-directory requires read-only user, group and client visibility")
    if directory_roles - {"view-users", "query-users", "query-groups", "view-clients"}:
        errors.append("realm: dx-directory must retain only the reviewed read-only roles")
    web = clients.get("dx-web", {})
    app = platform.get("APP_BASE_URL", "").rstrip("/")
    if not web.get("enabled", True) or not web.get("publicClient") or not web.get("standardFlowEnabled") or web.get("implicitFlowEnabled") or web.get("attributes", {}).get("pkce.code.challenge.method") != "S256":
        errors.append("realm: dx-web must use authorization code with S256 PKCE")
    if not web.get("redirectUris") or any(not value.startswith(app + "/") or "*" in value for value in web.get("redirectUris", [])):
        errors.append("realm: require exact application redirect URLs without wildcards")
    if web.get("webOrigins") != [app]:
        errors.append("realm: restrict dx-web webOrigins to the application origin")
    for client in clients.values():
        if client.get("enabled", True) and client.get("directAccessGrantsEnabled"):
            errors.append("realm: password grants must not be enabled")
    for name, secret in (("dx-directory", credentials[4]), ("dx-assessment-worker", credentials[5])):
        if clients.get(name, {}).get("secret") != secret:
            errors.append("realm: service account secret must match " + name)
    return errors


def self_test():
    secrets = [str(index) * 40 for index in range(1, 7)]
    services = {name: {"image": "registry/example@sha256:" + "a" * 64} for name in
                ("postgres", "keycloak", "platform", "gateway", "frontend", "intelligence")}
    services["postgres"]["environment"] = dict(POSTGRES_USER="bootstrap", POSTGRES_PASSWORD=secrets[0],
        APP_DATABASE_PASSWORD=secrets[1], IDENTITY_DATABASE_PASSWORD=secrets[2])
    services["platform"]["environment"] = dict(APP_BASE_URL="https://desk.example.org", JWT_ISSUER="https://identity.example.org/realms/dx-lab",
        SPRING_PROFILES_ACTIVE="production", DX_ALLOW_UNSCANNED_UPLOAD="false", POSTGRES_PASSWORD=secrets[1],
        INTELLIGENCE_TRIAGE_BASE_URL="http://intelligence:8000", INTELLIGENCE_TRIAGE_TOKEN="7" * 40,
        DX_DIRECTORY_CLIENT_SECRET=secrets[4], DX_BACKGROUND_CLIENT_SECRET=secrets[5])
    services["intelligence"]["environment"] = dict(INTELLIGENCE_TRIAGE_TOKEN="7" * 40)
    services["keycloak"].update(command=["start"], environment=dict(KC_HOSTNAME="https://identity.example.org", KC_PROXY_HEADERS="xforwarded",
        KC_PROXY_TRUSTED_ADDRESSES="172.18.0.1/32", KC_DB_USERNAME="identity", KC_DB_PASSWORD=secrets[2], KC_BOOTSTRAP_ADMIN_PASSWORD=secrets[3]))
    realm = dict(realm="dx-lab", sslRequired="external", clients=[dict(clientId="dx-web", publicClient=True, standardFlowEnabled=True,
        attributes={"pkce.code.challenge.method": "S256"}, redirectUris=["https://desk.example.org/auth/callback"], webOrigins=["https://desk.example.org"]),
        dict(clientId="dx-directory", secret=secrets[4], optionalClientScopes=["kc-admin-api"]), dict(clientId="dx-assessment-worker", secret=secrets[5])])
    realm["users"] = [dict(serviceAccountClientId="dx-directory", clientRoles={"realm-management": ["view-users", "query-users", "query-groups", "view-clients"]})]
    config = {"services": services}
    assert not validate(config, realm)
    invalid = copy.deepcopy(realm)
    invalid["clients"][1]["optionalClientScopes"] = []
    assert validate(config, invalid)
    for roles in (["view-users", "query-users", "query-groups"], ["view-users", "query-users", "query-groups", "view-clients", "manage-users"]):
        invalid = copy.deepcopy(realm)
        invalid["users"][0]["clientRoles"]["realm-management"] = roles
        assert validate(config, invalid)
    for service, key, value in (("platform", "DX_ALLOW_UNSCANNED_UPLOAD", "true"), ("keycloak", "KC_HOSTNAME", "http://identity.example.org"),
                                ("platform", "APP_BASE_URL", "https://desk.example.org/"),
                                ("keycloak", "KC_PROXY_TRUSTED_ADDRESSES", "0.0.0.0/0"), ("postgres", "POSTGRES_PASSWORD", "short"),
                                ("intelligence", "INTELLIGENCE_TRIAGE_TOKEN", ""), ("platform", "INTELLIGENCE_TRIAGE_TOKEN", "mismatch")):
        invalid = copy.deepcopy(config)
        invalid["services"][service]["environment"][key] = value
        assert validate(invalid, realm), (service, key)
    for mutate in (lambda item: item["services"]["platform"].update(image="registry/example:latest"),
                   lambda item: item["services"]["frontend"].update(ports=[{"host_ip": "0.0.0.0"}]),
                   lambda item: item["services"]["keycloak"].update(command=["start-dev"])):
        invalid = copy.deepcopy(config)
        mutate(invalid)
        assert validate(invalid, realm)
    for changes in (dict(users=[dict(username="requester1")]), dict(sslRequired="none")):
        assert validate(config, dict(realm, **changes))
    for changes in (dict(publicClient=False), dict(enabled=False), dict(implicitFlowEnabled=True),
                    dict(directAccessGrantsEnabled=True), dict(redirectUris=["https://attacker.example.org/callback"]),
                    dict(redirectUris=["https://desk.example.org/*"]), dict(webOrigins=["*"])):
        invalid = copy.deepcopy(realm)
        invalid["clients"][0].update(changes)
        assert validate(config, invalid), changes
    print("PASS production preflight: HTTPS, immutable images, proxy trust, roles, secrets, realm and negative cases")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--project")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.env_file or not args.project or not re.fullmatch(r"production-[a-z0-9-]{1,40}", args.project):
        parser.error("require --env-file and an explicit production- project name; this command never deploys")
    command = ["docker", "compose", "--env-file", str(args.env_file), "-p", args.project, "-f", "compose.yaml", "-f",
               "infra/compose/compose.triage.yaml", "-f", "infra/compose/compose.production.yaml", "--profile", "intelligence", "config", "--format", "json"]
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        raise SystemExit("Compose resolution failed; verify required production settings (output withheld to protect secrets)")
    config = json.loads(result.stdout)
    mounts = config["services"]["keycloak"].get("volumes", [])
    realm_file = next((mount["source"] for mount in mounts if mount.get("target") == "/opt/keycloak/data/import/realm.json"), None)
    if not realm_file:
        raise SystemExit("A reviewed production realm file is required")
    errors = validate(config, json.loads(Path(realm_file).read_text(encoding="utf-8-sig")))
    if errors:
        raise SystemExit("Production configuration rejected:\n- " + "\n- ".join(errors))
    print("PASS configuration only; external TLS, image provenance, security scan and operational acceptance are separate gates")


if __name__ == "__main__":
    main()
