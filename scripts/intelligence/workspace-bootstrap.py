"""Local-development IAM bootstrap for a configured service account, never human users."""

import argparse
import hmac
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import logging
import os
from pathlib import Path
import re
from threading import Lock
import time
import urllib.error
import urllib.parse
import urllib.request


class Denied(Exception):
    pass


def require(condition):
    if not condition:
        raise ValueError("Invalid bootstrap configuration or response")


def secret(path):
    source = Path(path)
    require(source.stat().st_size <= 4096)
    value = source.read_text("utf-8").strip()
    require(
        1 <= len(value) <= 4096
        and all(ord(char) >= 32 and ord(char) != 127 for char in value)
    )
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("Redirects are disabled")


class Keycloak:
    def __init__(self, base, password_file, realm="dx-lab", username="admin"):
        uri = urllib.parse.urlparse(base)
        require(
            uri.scheme == "http"
            and uri.hostname in ("keycloak", "127.0.0.1", "localhost")
            and uri.port
            and not uri.username
            and not uri.query
            and not uri.fragment
            and uri.path in ("", "/")
        )
        require(re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", realm))
        self.base, self.password_file, self.realm, self.username = (
            base.rstrip("/"),
            password_file,
            realm,
            username,
        )
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirect()
        )
        self.token, self.expiry = None, 0

    def login(self):
        body = urllib.parse.urlencode(
            {
                "grant_type": "password",
                "client_id": "admin-cli",
                "username": self.username,
                "password": secret(self.password_file),
            }
        ).encode()
        request = urllib.request.Request(
            self.base + "/realms/master/protocol/openid-connect/token",
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        with self.opener.open(request, timeout=3) as response:
            raw = response.read(65537)
        require(len(raw) <= 65536)
        result = json.loads(raw)
        require(type(result.get("access_token")) is str and result["access_token"])
        self.token, self.expiry = result["access_token"], time.monotonic() + max(
            1, min(240, int(result.get("expires_in", 60)) - 15)
        )

    def call(self, method, path, payload=None, *, missing=False):
        if time.monotonic() >= self.expiry:
            self.login()
        body = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(
            self.base + "/admin/realms/" + self.realm + path,
            data=body,
            method=method,
            headers={
                "Authorization": "Bearer " + self.token,
                "Content-Type": "application/json",
            },
        )
        try:
            with self.opener.open(request, timeout=3) as response:
                raw = response.read(1048577)
            require(len(raw) <= 1048576)
            return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            if error.code == 401:
                self.expiry = 0
            raise


class WorkspaceBootstrap:
    def __init__(self, api, state_file, policy):
        self.api, self.state_file, self.policy = api, Path(state_file), policy
        require(set(policy) == {"version", "clientId", "permissionClient", "groups"})
        require(
            policy["clientId"] == "dx-assessment-worker"
            and policy["permissionClient"] == "dx-permissions"
        )
        require(
            policy["groups"]
            == {
                "assessment-worker": ["intelligence:process", "task:handle"],
                "alert-worker": ["alerts:process"],
            }
        )
        self.lock = Lock()
        if self.state_file.exists():
            require(self.state_file.stat().st_size <= 1048576)
            self.state = json.loads(self.state_file.read_text("utf-8"))
            require(type(self.state) is dict)
        else:
            self.state = {}

    def save(self):
        raw = json.dumps(self.state)
        require(len(raw) <= 1048576)
        self.state_file.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.state_file.with_suffix(".tmp")
        temporary.write_text(raw, encoding="utf-8")
        os.replace(temporary, self.state_file)

    def client(self, name):
        clients = self.api.call(
            "GET", "/clients?clientId=" + urllib.parse.quote(name, safe="")
        )
        require(
            type(clients) is list
            and len(clients) == 1
            and clients[0].get("clientId") == name
        )
        return clients[0]["id"]

    def ensure(self, workspace):
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", workspace):
            raise Denied()
        with self.lock:
            parent = self.api.call(
                "GET", "/group-by-path/workspaces/" + workspace, missing=True
            )
            if parent is None or parent.get("path") != "/workspaces/" + workspace:
                raise Denied()
            worker_client = self.client(self.policy["clientId"])
            worker = self.api.call(
                "GET", "/clients/" + worker_client + "/service-account-user"
            )
            if worker.get("username") != "service-account-" + self.policy[
                "clientId"
            ] or not worker.get("enabled", False):
                raise Denied()
            permission_client = self.client(self.policy["permissionClient"])
            identity = parent["id"] + ":" + worker["id"]
            if identity not in self.state:
                self.state[identity] = {"initialized": False, "created": []}
                self.save()
            record = self.state[identity]
            require(type(record) is dict and set(record) == {"initialized", "created"})
            memberships = self.api.call(
                "GET", "/users/" + worker["id"] + "/groups?first=0&max=1000"
            )
            require(type(memberships) is list and len(memberships) < 1000)
            membership_ids = {item["id"] for item in memberships}
            for role, permissions in self.policy["groups"].items():
                path = "/group-by-path/workspaces/" + workspace + "/" + role
                group = self.api.call("GET", path, missing=True)
                if group is None:
                    if record["initialized"]:
                        raise Denied()  # Deletion/revocation is never silently undone.
                    self.api.call(
                        "POST", "/groups/" + parent["id"] + "/children", {"name": role}
                    )
                    group = self.api.call("GET", path)
                    record["created"].append(group["id"])
                    self.save()
                mappings_path = (
                    "/groups/"
                    + group["id"]
                    + "/role-mappings/clients/"
                    + permission_client
                )
                current = self.api.call("GET", mappings_path)
                require(type(current) is list)
                present = {entry["name"] for entry in current}
                missing = [
                    permission
                    for permission in permissions
                    if permission not in present
                ]
                if missing:
                    if record["initialized"] or group["id"] not in record["created"]:
                        raise Denied()  # Existing IAM decisions belong to the administrator.
                    roles = [
                        self.api.call(
                            "GET",
                            "/clients/"
                            + permission_client
                            + "/roles/"
                            + urllib.parse.quote(permission, safe=""),
                        )
                        for permission in missing
                    ]
                    self.api.call("POST", mappings_path, roles)
                if group["id"] not in membership_ids:
                    if record["initialized"]:
                        raise Denied()
                    self.api.call(
                        "PUT", "/users/" + worker["id"] + "/groups/" + group["id"]
                    )
            record["initialized"] = True
            self.save()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def authorized(self):
        values = self.headers.get_all("Authorization", [])
        return (
            len(values) == 1
            and values[0].isascii()
            and hmac.compare_digest(values[0], "Bearer " + self.server.token)
        )

    def reply(self, status, value):
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        (
            self.reply(200, {"ready": True})
            if self.authorized() and self.path == "/health/ready"
            else self.reply(403, {"ready": False})
        )

    def do_POST(self):
        if not self.authorized():
            self.reply(403, {"ready": False})
            return
        match = re.fullmatch(r"/internal/workspaces/([a-zA-Z0-9_-]{1,100})", self.path)
        if (
            not match
            or self.headers.get("Content-Length", "0") != "0"
            or self.headers.get("Transfer-Encoding")
        ):
            self.reply(403, {"ready": False})
            return
        try:
            self.server.bootstrap.ensure(match[1])
            self.reply(200, {"workspace": match[1], "ready": True})
        except Denied:
            self.reply(403, {"ready": False})
        except Exception:
            logging.warning(
                "Workspace bootstrap could not verify IAM; no AI input was processed"
            )
            self.reply(503, {"ready": False})


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 4

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(3)
        return connection, address

    def handle_error(self, *_):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--health", action="store_true")
    args = parser.parse_args()
    token = secret(os.environ["WORKSPACE_BOOTSTRAP_TOKEN_FILE"])
    require(len(token) >= 32 and token.isascii())
    if args.health:
        request = urllib.request.Request(
            "http://127.0.0.1:8005/health/ready",
            headers={"Authorization": "Bearer " + token},
        )
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
            request, timeout=2
        ) as response:
            require(json.loads(response.read(4096))["ready"] is True)
        return
    require(os.environ.get("WORKSPACE_BOOTSTRAP_ENVIRONMENT") == "local-dev")
    api = Keycloak(
        os.environ["WORKSPACE_BOOTSTRAP_KEYCLOAK_URL"],
        os.environ["WORKSPACE_BOOTSTRAP_ADMIN_PASSWORD_FILE"],
    )
    api.login()
    policy_path = Path(os.environ["WORKSPACE_BOOTSTRAP_POLICY_FILE"])
    digest = sha256(
        (
            sha256(Path(__file__).read_bytes()).hexdigest()
            + ":"
            + sha256(policy_path.read_bytes()).hexdigest()
        ).encode()
    ).hexdigest()
    require(digest == os.environ["WORKSPACE_BOOTSTRAP_CODE_DIGEST"])
    bootstrap = WorkspaceBootstrap(
        api,
        "/state/provisioned.json",
        json.loads(policy_path.read_text("utf-8")),
    )
    with Server(("0.0.0.0", 8005), Handler) as server:
        server.token, server.bootstrap = token, bootstrap
        server.serve_forever(poll_interval=0.2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        raise SystemExit(
            "Workspace bootstrap configuration or Keycloak authentication failed"
        ) from None
