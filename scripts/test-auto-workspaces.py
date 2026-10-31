"""Local DEV acceptance: creates synthetic tickets and a new workspace for demo requester2.

Uses actual Keycloak, worker, rules and model. Never resets the realm or deletes data.
"""

import argparse
import json
from pathlib import Path
import runpy
import time
import traceback
import urllib.parse
import urllib.error
import urllib.request
import uuid

HELPERS = runpy.run_path(str(Path(__file__).with_name("test-local-model.py")))
login, request, require = (HELPERS[name] for name in ("login", "request", "require"))
ROOT = HELPERS["ROOT"]
LAST_OPERATION = "initialization"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    config = dict(
        line.split("=", 1)
        for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    require(
        config.get("LOCAL_MODEL_WORKSPACES") == "auto",
        "Automatic workspace mode is required",
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    auth = urllib.parse.urlencode(
        {
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": "admin",
            "password": config["KC_ADMIN_PASSWORD"],
        }
    ).encode()
    admin_token, admin_expiry = None, 0

    def admin(method, path, value=None):
        global LAST_OPERATION
        nonlocal admin_token, admin_expiry
        LAST_OPERATION = method + " " + path.split("?", 1)[0]
        if time.monotonic() >= admin_expiry:
            with opener.open(
                "http://localhost:8081/realms/master/protocol/openid-connect/token",
                data=auth,
                timeout=10,
            ) as response:
                credentials = json.load(response)
            admin_token = credentials["access_token"]
            admin_expiry = time.monotonic() + max(
                1, int(credentials.get("expires_in", 60)) - 15
            )
        data = None if value is None else json.dumps(value).encode()
        call = urllib.request.Request(
            "http://localhost:8081/admin/realms/dx-lab" + path,
            data=data,
            method=method,
            headers={
                "Authorization": "Bearer " + admin_token,
                "Content-Type": "application/json",
            },
        )
        with opener.open(call, timeout=10) as response:
            raw = response.read()
            return (
                json.loads(raw)
                if raw
                else response.headers.get("Location", "").rsplit("/", 1)[-1]
            )

    user = login("requester2", config["DEMO_PASSWORD"])
    foreign = login("requester1", config["DEMO_PASSWORD"])

    def create(workspace, token):
        base = "/workspaces/" + workspace + "/tickets"
        code, status = request(
            "/workspaces/" + workspace + "/intelligence/status", token
        )
        require(
            code == 200 and status["modelEnabled"],
            "AI must apply in the authorized workspace",
        )
        code, ticket = request(
            base,
            token,
            {
                "clientRequestId": str(uuid.uuid4()),
                "title": "Máy 15 phòng 304 A1 không lên nguồn",
                "description": "Máy này tôi đang cần để làm bài thi gấp trong 15 phút nữa",
            },
        )
        require(code == 201, "Synthetic ticket creation failed")
        return base + "/" + ticket["id"], ticket["id"]

    def wait(path, token, expected="READY"):
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            code, ticket = request(path, token)
            require(code == 200, "Created ticket must remain visible")
            assessment = ticket.get("assessment")
            if assessment and assessment["status"] != "PENDING":
                require(
                    assessment["status"] == expected,
                    "Unexpected stored assessment state: "
                    + str(assessment.get("errorCode")),
                )
                if expected == "READY":
                    result = assessment["result"]
                    require(
                        result["engine"] == "hybrid"
                        and result["category"] == "HARDWARE"
                        and result["priority"] == "HIGH",
                        "Hybrid classification must remain correct in every workspace",
                    )
                    require(
                        result["cooperation"]["modelStatus"] == "AVAILABLE"
                        and result["cooperation"]["rulesStatus"] != "UNAVAILABLE",
                        "Model and rules must both execute",
                    )
                return assessment
            time.sleep(1)
        raise RuntimeError("Automatic assessment did not finish")

    path_b, ticket_b = create("lab-b", user)
    wait(path_b, user)
    print("PASS: model + rules in lab-b", flush=True)
    require(
        request(path_b, foreign)[0] in (403, 404),
        "Auto mode must not expand human access",
    )

    workspace = "ai-auto-" + uuid.uuid4().hex[:8]
    root = admin("GET", "/group-by-path/workspaces")
    workspace_created = admin(
        "POST",
        "/groups/" + root["id"] + "/children",
        {
            "name": workspace,
            "attributes": {"displayName": ["AI automatic workspace test"]},
        },
    )
    workspace_id = (
        workspace_created["id"]
        if isinstance(workspace_created, dict)
        else workspace_created
    )
    requester_created = admin(
        "POST", "/groups/" + workspace_id + "/children", {"name": "requester"}
    )
    requester = (
        requester_created["id"]
        if isinstance(requester_created, dict)
        else requester_created
    )
    permissions = admin("GET", "/clients?clientId=dx-permissions")[0]["id"]
    role = admin("GET", "/clients/" + permissions + "/roles/request%3Acreate")
    admin(
        "POST", "/groups/" + requester + "/role-mappings/clients/" + permissions, [role]
    )
    actor = admin("GET", "/users?username=requester2&exact=true")[0]["id"]
    admin("PUT", "/users/" + actor + "/groups/" + requester)
    user = login("requester2", config["DEMO_PASSWORD"])
    path_new, ticket_new = create(workspace, user)
    wait(path_new, user)
    print(
        "PASS: workspace created after startup without environment changes", flush=True
    )
    require(
        request(path_new, foreign)[0] in (403, 404),
        "New workspace must retain ticket authorization",
    )

    worker_client = admin("GET", "/clients?clientId=dx-assessment-worker")[0]["id"]
    worker = admin("GET", "/clients/" + worker_client + "/service-account-user")["id"]
    group = admin(
        "GET", "/group-by-path/workspaces/" + workspace + "/assessment-worker"
    )["id"]
    admin("DELETE", "/users/" + worker + "/groups/" + group)
    try:
        revoked_path, _ = create(workspace, user)
        revoked = wait(revoked_path, user, "DEGRADED")
        require(
            revoked["errorCode"] == "AUTHORIZATION_REVOKED",
            "Worker revocation must suppress AI",
        )
        memberships = admin("GET", "/users/" + worker + "/groups?max=1000")
        require(
            all(item["id"] != group for item in memberships),
            "Bootstrap must not undo administrator revocation",
        )
    finally:
        admin("PUT", "/users/" + worker + "/groups/" + group)
    require(
        request(revoked_path + "/assessment/retry", user, {})[0] == 200,
        "Restored worker must permit explicit retry",
    )
    wait(revoked_path, user)
    proof = {
        "result": "PASS",
        "existingWorkspace": "lab-b",
        "existingTicket": ticket_b,
        "createdWorkspaceAfterStartup": workspace,
        "newTicket": ticket_new,
        "hybridInBothWorkspaces": True,
        "crossWorkspaceDenied": True,
        "workerRevocationRespected": True,
        "restoredWorkerRetry": True,
    }
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(proof))


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        raise SystemExit(str(error)) from None
    except urllib.error.HTTPError as error:
        raise SystemExit(
            "Automatic workspace acceptance failed: HTTP "
            + str(error.code)
            + " during "
            + LAST_OPERATION
        ) from None
    except Exception as error:
        frame = traceback.extract_tb(error.__traceback__)[-1]
        raise SystemExit(
            "Automatic workspace acceptance failed: "
            + type(error).__name__
            + " at "
            + Path(frame.filename).name
            + ":"
            + str(frame.lineno)
        ) from None
