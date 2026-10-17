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
for username in ("user1", "user2", "user3", "user4", "user7"):
    payload = urllib.parse.urlencode(dict(grant_type="password", client_id="core-probe",
                                         username=username, password=settings["DEMO_PASSWORD"])).encode()
    with urllib.request.urlopen("http://localhost:8081/realms/dx-lab/protocol/openid-connect/token", payload, timeout=30) as response:
        tokens[username] = json.load(response)["access_token"]


def call(method, path, user, expected, body=None, core=False):
    headers = {"X-Request-ID": str(uuid.uuid4())}
    if user:
        headers["Authorization"] = "Bearer " + tokens[user]
    payload = None if body is None else json.dumps(body).encode()
    if payload is not None:
        headers["Content-Type"] = "application/json"
    prefix = "/api/core/v1" if core else "/api/apps/servicedesk/v1"
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
        if not core and raw:
            responses.append(dict(method=method, path=path, status=response.status, bodyJson=raw))
        print(f"PASS {method} {prefix}{path} -> {response.status}")
        return result


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
            assert call("GET", path, owner, 200) == ticket
            progress = call("GET", path + "/progress", owner, 200)
            assert progress["id"] == ticket["process"]["processInstanceId"]
            assert progress["steps"] and (progress["ended"] or progress["currentTasks"])
        print(f"PASS {len(tickets)} recovered tickets retain content, ownership, process reference and readable engine progress")
    finally:
        Path(sys.argv[1]).write_text(json.dumps(responses, ensure_ascii=True, indent=2), encoding="utf-8")
    raise SystemExit(0)

base = "/workspaces/lab-a/tickets"
marker = "ticket-e2e-" + str(uuid.uuid4())
payload = dict(clientRequestId=str(uuid.uuid4()), title=marker, description="Synthetic network support request")
try:
    call("POST", base, None, 401, payload)
    call("POST", base, "user4", 403, payload)
    call("POST", base, "user7", 403, payload)
    call("POST", base, "user1", 400, dict(payload, owner="spoof"))
    call("POST", base, "user1", 400, dict(payload, title=" "))
    ticket = call("POST", base, "user1", 201, payload)
    assert ticket["creator"] == subjects["user1"]
    assert ticket["process"]["businessKey"] == ticket["id"] and ticket["process"]["processKey"] == "service-desk"
    path = base + "/" + ticket["id"]
    call("POST", base, "user1", 409, dict(payload, title="Changed duplicate"))
    call("GET", path, "user1", 200)
    call("GET", path, None, 401)
    call("GET", path, "user4", 403)
    call("GET", path.replace("lab-a", "lab-b"), "user4", 404)
    call("GET", base + "/" + str(uuid.uuid4()), "user1", 404)
    other = call("POST", base, "user2", 201, dict(payload, clientRequestId=str(uuid.uuid4()), title=marker + " other"))
    call("GET", base + "/" + other["id"], "user1", 404)
    search = base + "?" + urllib.parse.urlencode(dict(q=marker, size=1))
    own = call("GET", search, "user1", 200)
    assert own["total"] == 1 and len(own["items"]) == 1
    assert call("GET", search, "user2", 200)["total"] == 2
    call("GET", base + "?size=101", "user1", 400)
    call("GET", base + "/not-a-uuid", "user1", 400)
    call("GET", path + "/progress", "user2", 404)
    instance = "/workspaces/lab-a/instances/" + ticket["process"]["processInstanceId"]
    for outcome in ("rework", "accepted"):
        progress = call("GET", path + "/progress", "user1", 200)
        task = progress["currentTasks"][0]
        assert task["key"] == "handle"
        inbox = call("GET", "/workspaces/lab-a/tasks?scope=candidate", "user2", 200, core=True)
        assert any(item["id"] == task["id"] for item in inbox["items"])
        task_path = instance + "/tasks/" + task["id"]
        call("POST", task_path + "/claim", "user1", 403, core=True)
        call("POST", task_path + "/claim", "user2", 200, core=True)
        call("POST", task_path + "/complete", "user1", 403,
             dict(expectedTaskKey="handle", outcome="resolved"), core=True)
        call("POST", task_path + "/complete", "user2", 204,
             dict(expectedTaskKey="handle", outcome="resolved"), core=True)
        progress = call("GET", path + "/progress", "user1", 200)
        confirm = progress["currentTasks"][0]
        assert confirm["key"] == "confirm" and confirm["assignee"] == subjects["user1"]
        confirm_path = instance + "/tasks/" + confirm["id"] + "/complete"
        call("POST", confirm_path, "user2", 403, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
        call("POST", confirm_path, "user1", 204, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
        call("POST", confirm_path, "user1", 409, dict(expectedTaskKey="confirm", outcome=outcome), core=True)
    progress = call("GET", path + "/progress", "user1", 200)
    assert progress["ended"] and len(progress["steps"]) == 4 and not progress["currentTasks"]
    assert [step["assignee"] for step in progress["steps"]] == [subjects["user2"], subjects["user1"]] * 2
    race = dict(payload, clientRequestId=str(uuid.uuid4()), title=marker + " race")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call("POST", base, "user1", (201, 409), race), range(2)))
    assert sorted(201 if "id" in result else result["status"] for result in results) == [201, 409]
    created = [ticket, other, next(result for result in results if "id" in result)]
    events = call("GET", "/workspaces/lab-a/audit?action=servicedesk.ticket.create&size=100", "user3", 200, core=True)["items"]
    for item in created:
        matching = [event for event in events if event["resource"] == item["id"]]
        assert len(matching) == 1 and matching[0]["actor"] == item["creator"]
        assert matching[0]["details"] == dict(processInstanceId=item["process"]["processInstanceId"])
    print("PASS real Keycloak -> APISIX -> Ticket -> Flowable -> Audit, rework, confirmation and concurrent duplicate")
finally:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".local/servicedesk-http.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(responses, ensure_ascii=True, indent=2), encoding="utf-8")
