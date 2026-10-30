"""R9 HTTP proof against an isolated running stack and real Keycloak tokens."""
import argparse
import base64
import json
import os
import subprocess
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:18080")
    parser.add_argument("--keycloak", default="http://localhost:8081")
    parser.add_argument("--evidence", type=Path, default=Path(".local/r9-runtime-evidence.json"))
    parser.add_argument("--verify-restart", action="store_true")
    parser.add_argument("--sla-fixture-container", help="Isolated test PostgreSQL container only; ages one synthetic ticket for real worker escalation")
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    settings = dict(line.split("=", 1) for line in (root / ".env").read_text(encoding="utf-8-sig").splitlines()
                    if "=" in line and not line.startswith("#"))
    password = os.environ.get("R9_DEMO_PASSWORD", settings.get("DEMO_PASSWORD"))
    if not password:
        raise SystemExit("R9_DEMO_PASSWORD or local DEMO_PASSWORD required")

    def token(username):
        form = urllib.parse.urlencode(dict(grant_type="password", client_id="core-probe", username=username, password=password))
        request = urllib.request.Request(arguments.keycloak + "/realms/dx-lab/protocol/openid-connect/token", data=form.encode())
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)["access_token"]

    requester, agent, coordinator, foreign = (token(name) for name in ("user1", "user2", "user3", "user5"))
    payload = agent.split(".")[1]
    agent_id = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))["sub"]
    responses = []

    def call(method, path, bearer, body=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        request = urllib.request.Request(arguments.base + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            status = response.status
            raw = response.read().decode()
        parsed = json.loads(raw) if raw else None
        assert status == expected, (method, path, expected, status, parsed)
        responses.append(dict(method=method, path=path, status=status, body=parsed))
        return parsed

    tickets = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets"
    work = "/api/hpdi/v1/workspaces/lab-a"

    def fixture_sql(statement):
        return subprocess.run(["docker", "exec", "-i", arguments.sla_fixture_container,
                               "psql", "-U", "core", "-d", "core", "-At", "-v", "ON_ERROR_STOP=1"],
                              input=statement, text=True, capture_output=True, check=True).stdout.strip()

    if arguments.verify_restart:
        evidence = json.loads(arguments.evidence.read_text(encoding="utf-8"))
        for expected in evidence["retained"]:
            current = call("GET", tickets + "/" + expected["id"], coordinator)
            for field in ("process", "taskId", "classification", "estimate", "corrections", "workState", "state", "assignee"):
                assert current[field] == expected[field], (expected["id"], field)
        inbox = call("GET", work + "/alerts?size=100", coordinator)
        persisted = {item["id"] for item in inbox["items"]}
        assert set(evidence["criticalCorrectionAlerts"]) <= persisted
        if "reaffirmedTicket" in evidence:
            assert arguments.sla_fixture_container, "Same isolated SLA fixture container required for restart proof"
            ticket_id = str(uuid.UUID(evidence["reaffirmedTicket"]))
            assert fixture_sql("select manual_priority from servicedesk_sla where ticket_id='" + ticket_id
                               + "' and released_at is null") == "LOW"
        evidence["restartVerified"] = True
        evidence["restartResponses"] = responses
        arguments.evidence.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
        print("PASS R9 restart persistence:", len(evidence["retained"]), "tickets")
        return

    call("PUT", work + "/duty/me", agent, {"onDuty": True})

    def reviewed(priority="HIGH"):
        created = call("POST", tickets, requester, dict(clientRequestId=str(uuid.uuid4()), title="R9 runtime " + str(uuid.uuid4()),
                                                       description="Synthetic R9 runtime acceptance."), 201)
        result = call("POST", tickets + "/" + created["id"] + "/review", coordinator,
                      dict(category="OTHER", priority=priority, assignee=agent_id, reason="Independent runtime review"))
        return result

    first = reviewed()
    route = tickets + "/" + first["id"] + "/classification"
    correction = dict(priority="CRITICAL", reason="Verified impact")
    call("PATCH", route, None, correction, 401)
    call("PATCH", route, requester, correction, 403)
    call("PATCH", route, foreign, correction, 403)
    call("PATCH", tickets + "/" + str(uuid.uuid4()) + "/classification", coordinator, correction, 404)
    call("PATCH", route, agent, dict(priority=0, reason="Invalid ordinal"), 400)
    corrected = call("PATCH", route, agent, correction)
    assert corrected["taskId"] == first["taskId"]
    assert corrected["classification"]["priority"] == "CRITICAL"
    assert corrected["estimate"]["minutes"] == 90
    assert len(corrected["corrections"]) == 1
    call("PATCH", route, agent, correction, 409)
    call("GET", work + "/alerts", requester, expected=403)
    queue = call("GET", work + "/queue", agent)
    queued = next(item for item in queue["items"] if item["id"] == first["taskId"])
    assert queued["priority"] == "CRITICAL"
    call("PATCH", route, agent, correction, 409)
    private = call("GET", tickets + "/" + first["id"], requester)
    assert "corrections" not in private

    second = reviewed()
    returned = call("PATCH", tickets + "/" + second["id"] + "/classification", coordinator,
                    dict(category="NETWORK", reason="Confirmed changed support group"))
    assert returned["state"] == "AWAITING_REVIEW"
    assert returned["process"] == second["process"]
    assert returned["taskId"] != second["taskId"]
    assert returned["assignee"] is None
    queue = call("GET", work + "/queue", agent)
    assert all(item["id"] != second["taskId"] for item in queue["items"])

    third = reviewed()
    task = work + "/tasks/" + third["taskId"]
    call("POST", task + "/start", agent)
    in_progress = call("PATCH", tickets + "/" + third["id"] + "/classification", coordinator,
                       dict(category="NETWORK", reason="Keep incumbent while investigating"))
    assert in_progress["taskId"] == third["taskId"] and in_progress["assignee"] == agent_id
    assert in_progress["workState"] == "IN_PROGRESS"
    call("POST", task + "/estimate-requests", agent, dict(proposedMinutes=120, reason="Changed diagnostics"), 201)
    call("POST", task + "/pause", agent, dict(reason="WAITING_REQUESTER", note="Need test evidence"))
    pending = call("PATCH", tickets + "/" + third["id"] + "/classification", agent,
                   dict(priority="CRITICAL", reason="Urgent while waiting"))
    assert pending["workState"] == "PENDING" and pending["sla"]["clockPaused"]
    estimates = call("GET", work + "/estimate-requests?status=SUPERSEDED", coordinator)
    assert any(item["taskId"] == third["taskId"] and item["supersededReason"] == "CLASSIFICATION_CHANGED" for item in estimates["items"])
    critical_alerts = []
    for attempt in range(15):
        inbox = call("GET", work + "/alerts?size=100", coordinator)
        critical_alerts = [item for item in inbox["items"] if item["type"] == "CLASSIFICATION_CHANGED"
                           and item["severity"] == "CRITICAL" and item["taskId"] in (first["taskId"], third["taskId"])]
        if len(critical_alerts) == 2:
            break
        time.sleep(1)
    else:
        raise AssertionError("Durable classification event did not reach Coordinator inbox")
    assert all("Priority corrected to CRITICAL" in item["label"] for item in critical_alerts)
    agent_inbox = call("GET", work + "/alerts?size=100", agent)
    assert not {item["id"] for item in critical_alerts} & {item["id"] for item in agent_inbox["items"]}
    evidence = dict(realKeycloak=True, runtimeBase=arguments.base, restartVerified=False,
                    criticalCorrectionAlerts=[item["id"] for item in critical_alerts],
                    retained=[corrected, returned, pending], responses=responses)
    if arguments.sla_fixture_container:
        fourth = reviewed("LOW")
        ticket_id = str(uuid.UUID(fourth["id"]))
        fixture_sql("update servicedesk_sla set approved_at=approved_at-interval '10 days',"
                    "start_by=start_by-interval '10 days',resolve_by=resolve_by-interval '10 days' "
                    "where ticket_id='" + ticket_id + "'")
        for attempt in range(45):
            escalated = call("GET", tickets + "/" + ticket_id, coordinator)
            if escalated["classification"]["priority"] == "MEDIUM":
                break
            time.sleep(2)
        else:
            raise AssertionError("Actual scheduled SLA worker did not escalate LOW to MEDIUM")
        before = json.loads(fixture_sql("select row_to_json(s) from servicedesk_sla s where ticket_id='" + ticket_id + "'"))
        reaffirmed = call("PATCH", tickets + "/" + ticket_id + "/classification", coordinator,
                          dict(priority="LOW", reason="Manually reaffirm LOW after automatic escalation"))
        assert reaffirmed["classification"]["priority"] == "LOW" and not reaffirmed["classification"]["escalated"]
        assert reaffirmed["corrections"] == [] and reaffirmed["estimate"] == fourth["estimate"]
        after = json.loads(fixture_sql("select row_to_json(s) from servicedesk_sla s where ticket_id='" + ticket_id + "'"))
        for field in ("approved_at", "start_by", "resolve_by", "start_budget_adjustment_millis", "resolve_budget_adjustment_millis"):
            assert before[field] == after[field], field
        assert after["manual_priority"] == "LOW" and after["start_breached_history"] and after["resolve_breached_history"]
        queue = call("GET", work + "/queue?size=100", agent)
        assert next(item for item in queue["items"] if item["id"] == fourth["taskId"])["priority"] == "LOW"
        call("POST", work + "/tasks/" + fourth["taskId"] + "/release", agent,
             dict(reason="HANDOVER", note="Preserve manual reaffirmation"), 204)
        continued = call("POST", tickets + "/" + ticket_id + "/review", coordinator,
                         dict(category="OTHER", priority="LOW", assignee=agent_id, reason="Continue manual LOW"))
        assert continued["classification"]["priority"] == "LOW"
        assert fixture_sql("select manual_priority from servicedesk_sla where ticket_id='" + ticket_id
                           + "' and released_at is null") == "LOW"
        evidence["reaffirmedTicket"] = ticket_id
        evidence["retained"].append(continued)
    arguments.evidence.parent.mkdir(parents=True, exist_ok=True)
    arguments.evidence.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    print("PASS R9 runtime:", len(responses), "real HTTP requests; Keycloak-issued JWTs")


if __name__ == "__main__":
    main()
