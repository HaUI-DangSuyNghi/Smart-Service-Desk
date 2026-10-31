"""Fixed synthetic HTTP acceptance; credentials and tokens never enter reports."""

import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def run(root, mode):
    settings = dict(line.split("=", 1) for line in (Path(root) / ".env").read_text(encoding="utf-8").splitlines()
                    if line and not line.startswith("#") and "=" in line)
    tokens = {}
    for role, username in (("requester", "requester1"), ("agent", "agent1"), ("coordinator", "coordinator1")):
        body = urllib.parse.urlencode({"client_id": "core-probe", "grant_type": "password", "username": username,
                                       "password": settings["DEMO_PASSWORD"]}).encode()
        request = urllib.request.Request("http://localhost:8081/realms/dx-lab/protocol/openid-connect/token", data=body)
        with urllib.request.urlopen(request, timeout=10) as response:
            tokens[role] = json.load(response)["access_token"]

    def call(role, method, path, body=None, expected=200):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request("http://localhost:9080/api/" + path, data=data, method=method,
                                         headers={"Authorization": "Bearer " + tokens[role], "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                if response.status != expected:
                    raise RuntimeError("Synthetic HTTP acceptance failed")
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as failure:
            if failure.code != expected:
                raise RuntimeError("Synthetic HTTP acceptance failed") from None

    tickets = "apps/servicedesk/v1/workspaces/lab-a/tickets"
    ticket = call("requester", "POST", tickets, {"clientRequestId": str(uuid.uuid4()),
                   "title": "Máy 15 phòng 1304 không lên nguồn", "description": "Synthetic D2 acceptance fixture; no customer data."}, 201)
    path = tickets + "/" + ticket["id"]
    tasks = "hpdi/v1/workspaces/lab-a/tasks/"
    state = call("coordinator", "GET", path + "/intake")
    if state["reviewTaskMayExist"]:
        raise RuntimeError("True wait not active")
    checks = ["CREATE_TRUE_WAIT"]
    call("requester", "POST", path + "/intake/manual-release", {"expectedContextRevision": state["contextRevision"],
         "expectedVersion": state["version"], "operationId": str(uuid.uuid4()), "reason": "Synthetic negative permission"}, 403)
    checks.append("REQUESTER_RELEASE_DENIED")
    if mode == "AUTHORIZED_ASSISTED":
        deadline = time.monotonic() + 120
        while state["status"] not in ("REVIEW_RELEASED", "MANUAL_REQUIRED") and time.monotonic() < deadline:
            time.sleep(2)
            state = call("coordinator", "GET", path + "/intake")
        if state["status"] != "REVIEW_RELEASED" or state["finalAssessment"]["ai07"]["category"] is None:
            raise RuntimeError("Final model proposal not available")
        checks.append("PERSISTED_FINAL_MODEL_PROPOSAL")
        for _ in range(3):
            if call("coordinator", "GET", path + "/intake")["version"] != state["version"]:
                raise RuntimeError("Repeated read changed final result")
        checks.append("REPEATED_READ_STABLE")
    state = call("coordinator", "POST", path + "/intake/manual-release", {"expectedContextRevision": state["contextRevision"],
         "expectedVersion": state["version"], "operationId": str(uuid.uuid4()), "reason": "Synthetic acceptance: human intake disposition"})
    call("coordinator", "POST", path + "/review", {"category": "HARDWARE", "priority": "MEDIUM", "assignee": None,
         "reason": "Synthetic human review", "expectedContextRevision": state["contextRevision"], "expectedIntakeVersion": state["version"]})
    handle = call("coordinator", "GET", path)["taskId"]
    call("agent", "PUT", "hpdi/v1/workspaces/lab-a/duty/me", {"onDuty": True})
    call("agent", "POST", tasks + handle + "/claim")
    call("agent", "POST", tasks + handle + "/start")
    call("agent", "POST", tasks + handle + "/complete", {"expectedTaskKey": "handle", "outcome": "resolved"}, 204)
    confirm = call("requester", "GET", path)["taskId"]
    call("requester", "POST", tasks + confirm + "/complete", {"expectedTaskKey": "confirm", "outcome": "accepted"}, 204)
    if call("requester", "GET", path)["state"] != "CLOSED":
        raise RuntimeError("Synthetic workflow did not close")
    checks += ["HUMAN_REVIEW", "AGENT_EXECUTION", "REQUESTER_CONFIRMATION", "ONE_PROCESS_CLOSED"]
    return {"status": "PASS_SYNTHETIC_HTTP", "checks": checks, "customerDataUsed": False, "productionApproved": False}
