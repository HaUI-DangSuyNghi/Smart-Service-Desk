import concurrent.futures
import json
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

settings = dict(line.split("=", 1) for line in Path(".env").read_text().splitlines()
                if "=" in line and not line.startswith("#"))
tokens, subjects, responses = {}, {}, []
for username in ("requester1", "agent1", "coordinator1", "requester2", "admin1"):
    payload = urllib.parse.urlencode(dict(grant_type="password", client_id="core-probe",
                                         username=username, password=settings["DEMO_PASSWORD"])).encode()
    with urllib.request.urlopen("http://localhost:8081/realms/dx-lab/protocol/openid-connect/token", payload, timeout=30) as response:
        tokens[username] = json.load(response)["access_token"]


def call(method, path, user, expected, body=None, core=False, hpdi=False):
    headers = {"X-Request-ID": str(uuid.uuid4())}
    if user:
        headers["Authorization"] = "Bearer " + tokens[user]
    payload = None if body is None else json.dumps(body).encode()
    if payload is not None:
        headers["Content-Type"] = "application/json"
    prefix = "/api/core/v1" if core else "/api/hpdi/v1" if hpdi else "/api/apps/servicedesk/v1"
    request = urllib.request.Request("http://localhost:9080" + prefix + path, payload, headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        statuses = (expected,) if isinstance(expected, int) else expected
        assert response.status in statuses, f"{method} {path}: expected {statuses}, got {response.status}: {raw[:300]}"
        assert response.headers.get("X-Request-ID") == headers["X-Request-ID"]
        result = json.loads(raw) if raw else None
        if response.status >= 400:
            assert response.headers.get_content_type() == "application/problem+json"
            assert result["status"] == response.status and result["requestId"] == headers["X-Request-ID"]
        if not core and not hpdi and raw:
            responses.append(dict(method=method, path=path, status=response.status, bodyJson=raw))
        print(f"PASS {method} {prefix}{path} -> {response.status}")
        return result


def statistics(marker, user, handling, confirming, completed, workspace="lab-a", **filters):
    path = "/workspaces/" + workspace + "/tickets/statistics?" + urllib.parse.urlencode(dict(q=marker, **filters))
    result = call("GET", path, user, 200)
    expected = dict(workspace=workspace, visibility="own" if user in ("requester1", "requester2") else "workspace",
                          total=handling + confirming + completed, awaitingHandling=handling,
                          awaitingConfirmation=confirming, completed=completed, unclassified=0)
    assert {key: result[key] for key in expected} == expected, result
    partition = ("intake", "following", "awaitingReview", "assigned", "inProgress", "pending",
                 "reopened", "awaitingConfirmation", "closed", "unclassified")
    assert set(result) == set(expected) | set(partition) | {"overdue"}, result
    assert all(type(result[key]) is int and result[key] >= 0 for key in (*partition, "overdue")), result
    assert sum(result[key] for key in partition) == result["total"], result
    assert result["closed"] == completed and result["overdue"] <= handling, result
    assert sum(result[key] for key in ("awaitingReview", "assigned", "inProgress", "pending", "reopened")) == handling, result
    listed = call("GET", path.replace("/statistics?", "?"), user, 200)
    assert listed["total"] == result["total"]


for username in tokens:
    subjects[username] = call("GET", "/me", username, 200, core=True)["sub"]

if len(sys.argv) > 2:
    try:
        saved = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
        tickets = [json.loads(record["bodyJson"]) for record in saved if record["method"] == "POST" and record["status"] == 201]
        assert tickets, "No seed tickets to verify after recovery"
        for ticket in tickets:
            owner = next(username for username, subject in subjects.items() if subject == ticket["creator"])
            path = "/workspaces/" + ticket["workspace"] + "/tickets/" + ticket["id"]
            restored = call("GET", path, owner, 200)
            stable = ("id", "workspace", "creator", "clientRequestId", "title", "description", "createdAt", "process")
            assert {key: restored[key] for key in stable} == {key: ticket[key] for key in stable}
            progress = call("GET", path + "/progress", owner, 200)
            assert progress["id"] == ticket["process"]["processInstanceId"]
            assert progress["steps"] and (progress["ended"] or progress["currentTasks"])
        marker = tickets[0]["title"]
        statistics(marker, "requester1", 1, 0, 1)
        statistics(marker, "agent1", 2, 0, 1)
        statistics(marker, "coordinator1", 2, 0, 1)
        print(f"PASS {len(tickets)} recovered tickets retain content, ownership, process reference and readable engine progress")
    finally:
        Path(sys.argv[1]).write_text(json.dumps(responses, ensure_ascii=True, indent=2), encoding="utf-8")
    raise SystemExit(0)

base = "/workspaces/lab-a/tickets"
marker = "ticket-e2e-" + str(uuid.uuid4())
payload = dict(clientRequestId=str(uuid.uuid4()), title=marker, description="Synthetic network support request")
try:
    statistics(marker, "requester1", 0, 0, 0)
    statistics(marker, "requester2", 0, 0, 0, workspace="lab-b")
    for user, status in ((None, 401), ("requester2", 403), ("admin1", 403)):
        call("GET", base + "/statistics", user, status)
    for route in (base, base + "/statistics"):
        for filters in ({"from": "not-a-date"}, {"from": "2026-01-02T00:00:00Z", "to": "2026-01-01T00:00:00Z"}):
            call("GET", route + "?" + urllib.parse.urlencode(filters), "requester1", 400)
    call("POST", base, None, 401, payload)
    call("POST", base, "requester2", 403, payload)
    call("POST", base, "admin1", 403, payload)
    call("POST", base, "requester1", 400, dict(payload, owner="spoof"))
    call("POST", base, "requester1", 400, dict(payload, title=" "))
    ticket = call("POST", base, "requester1", 201, payload)
    assert ticket["creator"] == subjects["requester1"]
    assert ticket["process"]["businessKey"] == ticket["id"] and ticket["process"]["processKey"] == "service-desk-reviewed"
    path = base + "/" + ticket["id"]
    call("POST", base, "requester1", 409, dict(payload, title="Changed duplicate"))
    call("GET", path, "requester1", 200)
    call("GET", path, None, 401)
    call("GET", path, "requester2", 403)
    call("GET", path.replace("lab-a", "lab-b"), "requester2", 404)
    call("GET", base + "/" + str(uuid.uuid4()), "requester1", 404)
    other = call("POST", base, "agent1", 201, dict(payload, clientRequestId=str(uuid.uuid4()), title=marker + " other"))
    call("GET", base + "/" + other["id"], "requester1", 404)
    search = base + "?" + urllib.parse.urlencode(dict(q=marker, size=1))
    own = call("GET", search, "requester1", 200)
    assert own["total"] == 1 and len(own["items"]) == 1
    assert call("GET", search, "agent1", 200)["total"] == 2
    statistics(marker, "requester1", 1, 0, 0)
    statistics(marker, "agent1", 2, 0, 0)
    statistics(marker, "coordinator1", 2, 0, 0)
    statistics(marker, "requester2", 0, 0, 0, workspace="lab-b")
    statistics(marker, "requester1", 1, 0, 0, **{"from": ticket["createdAt"]})
    statistics(marker, "requester1", 0, 0, 0, to=ticket["createdAt"])
    call("GET", base + "?size=101", "requester1", 400)
    call("GET", base + "/not-a-uuid", "requester1", 400)
    call("GET", path + "/progress", "agent1", 200)
    call("GET", base + "/" + other["id"] + "/progress", "requester1", 404)
    instance = "/workspaces/lab-a/instances/" + ticket["process"]["processInstanceId"]
    call("PUT", "/workspaces/lab-a/duty/me", "agent1", 200, {"onDuty": True}, hpdi=True)
    decision = dict(category="OTHER", priority="MEDIUM", assignee=None, reason="Synthetic acceptance review")
    call("POST", path + "/review", "requester1", 403, decision)
    for outcome in ("rework", "accepted"):
        call("POST", path + "/review", "coordinator1", 200, decision)
        statistics(marker, "requester1", 1, 0, 0)
        progress = call("GET", path + "/progress", "requester1", 200)
        task = progress["currentTasks"][0]
        assert task["key"] == "handle"
        inbox = call("GET", "/workspaces/lab-a/tasks?scope=candidate", "agent1", 200, core=True)
        assert any(item["id"] == task["id"] for item in inbox["items"])
        task_path = instance + "/tasks/" + task["id"]
        call("POST", task_path + "/claim", "requester1", 403, core=True)
        call("POST", task_path + "/claim", "agent1", 200, core=True)
        statistics(marker, "requester1", 1, 0, 0)
        call("POST", "/workspaces/lab-a/tasks/" + task["id"] + "/start", "agent1", 200, hpdi=True)
        call("POST", task_path + "/complete", "requester1", 403,
             dict(expectedTaskKey="handle", outcome="resolved"), core=True)
        call("POST", task_path + "/complete", "agent1", 204,
             dict(expectedTaskKey="handle", outcome="resolved"), core=True)
        statistics(marker, "requester1", 0, 1, 0)
        statistics(marker, "coordinator1", 1, 1, 0)
        progress = call("GET", path + "/progress", "requester1", 200)
        confirm = progress["currentTasks"][0]
        assert confirm["key"] == "confirm" and confirm["assignee"] == subjects["requester1"]
        confirm_path = instance + "/tasks/" + confirm["id"] + "/complete"
        call("POST", confirm_path, "agent1", 403, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
        call("POST", confirm_path, "requester1", 204, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
        call("POST", confirm_path, "requester1", 409, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
    progress = call("GET", path + "/progress", "requester1", 200)
    assert progress["ended"] and len(progress["steps"]) == 6 and not progress["currentTasks"]
    assert [step["assignee"] for step in progress["steps"]] == [subjects["coordinator1"], subjects["agent1"], subjects["requester1"]] * 2
    statistics(marker, "requester1", 0, 0, 1)
    race = dict(payload, clientRequestId=str(uuid.uuid4()), title=marker + " race")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call("POST", base, "requester1", (201, 409), race), range(2)))
    assert sorted(201 if "id" in result else result["status"] for result in results) == [201, 409]
    created = [ticket, other, next(result for result in results if "id" in result)]
    statistics(marker, "requester1", 1, 0, 1)
    statistics(marker, "agent1", 2, 0, 1)
    statistics(marker, "coordinator1", 2, 0, 1)
    events = call("GET", "/workspaces/lab-a/audit?action=servicedesk.ticket.create&size=100", "coordinator1", 200, core=True)["items"]
    for item in created:
        matching = [event for event in events if event["resource"] == item["id"]]
        assert len(matching) == 1 and matching[0]["actor"] == item["creator"]
        assert matching[0]["details"] == dict(processInstanceId=item["process"]["processInstanceId"])
    print("PASS real Keycloak -> APISIX -> Ticket -> Flowable -> Audit, rework, confirmation and concurrent duplicate")
finally:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".local/servicedesk-http.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(responses, ensure_ascii=True, indent=2), encoding="utf-8")
