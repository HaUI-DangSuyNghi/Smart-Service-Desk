"""R12 real HTTP acceptance against an explicitly named disposable compose stack."""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:18080")
    parser.add_argument("--postgres", required=True)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--evidence", type=Path, default=Path(".local/r12-runtime.json"))
    parser.add_argument("--verify-restart", action="store_true")
    args = parser.parse_args()
    assert args.postgres.startswith("r12-"), "Only an isolated R12 fixture container is allowed"
    assert args.backend.startswith("r12-"), "Only an isolated R12 backend is allowed"
    settings = dict(line.split("=", 1) for line in Path(".env").read_text(encoding="utf-8-sig").splitlines()
                    if "=" in line and not line.startswith("#"))
    responses = []

    def token(username):
        form = urllib.parse.urlencode(dict(grant_type="password", client_id="core-probe", username=username,
                                           password=settings["DEMO_PASSWORD"]))
        with urllib.request.urlopen(urllib.request.Request(
                "http://localhost:8081/realms/dx-lab/protocol/openid-connect/token", data=form.encode()), timeout=30) as response:
            return json.load(response)["access_token"]

    requester, agent, coordinator, foreign = (token(name) for name in ("user1", "user2", "user3", "user5"))
    payload = agent.split(".")[1]
    agent_id = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))["sub"]
    tickets = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets"
    tasks = "/api/hpdi/v1/workspaces/lab-a/tasks/"

    def call(method, path, bearer=None, body=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        request = urllib.request.Request(args.base + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            raw = response.read().decode()
            parsed = json.loads(raw) if raw else None
            assert response.status == expected, (method, path, expected, response.status, parsed)
            responses.append(dict(method=method, path=path, status=response.status, body=parsed))
        return parsed

    def sql(statement):
        return subprocess.run(["docker", "exec", "-i", args.postgres, "psql", "-U", "core", "-d", "core",
                               "-At", "-v", "ON_ERROR_STOP=1"], input=statement, text=True,
                              capture_output=True, check=True).stdout.strip()

    evidence = json.loads(args.evidence.read_text()) if args.verify_restart else {}
    marker = evidence.get("marker", "R12-" + str(uuid.uuid4()))
    route = tickets + "/statistics?" + urllib.parse.urlencode(dict(q=marker))

    def stats(field=None, total=1):
        result = call("GET", route, requester)
        assert result["total"] == total, result
        if field:
            assert result[field] == 1, result
        partition = ("intake", "following", "awaitingReview", "assigned", "inProgress", "pending", "reopened",
                     "awaitingConfirmation", "closed", "unclassified")
        assert sum(result[name] for name in partition) == result["total"]
        assert result["closed"] == result["completed"]
        assert 0 <= result["overdue"] <= result["total"]
        return result

    if args.verify_restart:
        result = stats(total=2)
        assert result["closed"] == 2
        assert result == evidence["finalStatistics"]
        ticket = call("GET", tickets + "/" + evidence["automaticTicket"], requester)
        assert ticket["closedBy"] == "AUTO"
        assert sql("select count(*) from audit_event") == evidence["auditCount"]
        evidence.update(restartVerified=True, restartResponses=responses)
    else:
        call("PUT", "/api/hpdi/v1/workspaces/lab-a/duty/me", agent, dict(onDuty=True))
        stats(total=0)
        call("GET", route, expected=401)
        call("GET", route, foreign, expected=403)
        for role in (requester, agent, coordinator):
            assert call("GET", route, role)["total"] == 0

        def create():
            return call("POST", tickets, requester, dict(clientRequestId=str(uuid.uuid4()), title=marker,
                        description="Synthetic R12 acceptance"), 201)

        def review(ticket):
            return call("POST", tickets + "/" + ticket["id"] + "/review", coordinator,
                        dict(category="OTHER", priority="HIGH", assignee=agent_id, reason="R12 acceptance"))

        ticket = create()
        stats("awaitingReview")
        created = datetime.fromisoformat(ticket["createdAt"].replace("Z", "+00:00"))
        for offset in (timezone.utc, timezone(timedelta(hours=7))):
            bounds = dict(q=marker, **{"from": created.astimezone(offset).isoformat(),
                                      "to": (created + timedelta(microseconds=1)).astimezone(offset).isoformat()})
            assert call("GET", tickets + "/statistics?" + urllib.parse.urlencode(bounds), requester)["total"] == 1
            bounds.update({"from": (created - timedelta(microseconds=1)).isoformat(), "to": created.isoformat()})
            assert call("GET", tickets + "/statistics?" + urllib.parse.urlencode(bounds), requester)["total"] == 0
        current = review(ticket)
        stats("assigned")
        for action, field, body in (("start", "inProgress", None), ("pause", "pending", {"reason": "WAITING_REQUESTER"}),
                                    ("resume", "inProgress", None)):
            call("POST", tasks + current["taskId"] + "/" + action, agent, body)
            stats(field)
        call("POST", tasks + current["taskId"] + "/release", agent,
             dict(reason="HANDOVER", note="R12 continue diagnostics"), 204)
        stats("awaitingReview")
        for cycle in range(3):
            current = review(ticket)
            stats("assigned" if cycle == 0 else "reopened")
            call("POST", tasks + current["taskId"] + "/start", agent)
            stats("inProgress")
            call("POST", tasks + current["taskId"] + "/complete", agent,
                 dict(expectedTaskKey="handle", outcome="resolved"), 204)
            stats("awaitingConfirmation")
            current = call("GET", tickets + "/" + ticket["id"], requester)
            call("POST", tasks + current["taskId"] + "/complete", requester,
                 dict(expectedTaskKey="confirm", outcome="accepted" if cycle == 2 else "rework"), 204)
            stats("closed" if cycle == 2 else "reopened")

        automatic = create()
        current = review(automatic)
        call("POST", tasks + current["taskId"] + "/start", agent)
        call("POST", tasks + current["taskId"] + "/complete", agent, dict(expectedTaskKey="handle", outcome="resolved"), 204)
        automatic_id = str(uuid.UUID(automatic["id"]))
        sql("begin; update act_ru_task set create_time_=create_time_-interval '73 hours' where id_ in"
            " (select generation from servicedesk_confirmation where ticket_id='" + automatic_id + "');"
            " update act_hi_taskinst set start_time_=start_time_-interval '73 hours' where id_ in"
            " (select generation from servicedesk_confirmation where ticket_id='" + automatic_id + "');"
            " update servicedesk_confirmation set handle_resolved_at=handle_resolved_at-interval '73 hours',"
            "confirm_started_at=confirm_started_at-interval '73 hours',deadline_at=deadline_at-interval '73 hours'"
            " where ticket_id='" + automatic_id + "'; commit;")
        deadline = time.monotonic() + 100
        while True:
            current = call("GET", tickets + "/" + automatic_id, requester)
            if current["state"] == "CLOSED":
                break
            assert time.monotonic() < deadline, "R11 worker did not AUTO-close disposable fixture"
            time.sleep(2)
        assert current["closedBy"] == "AUTO"
        final = stats(total=2)
        assert final["closed"] == 2 and final["overdue"] == 0
        audits = sql("select count(*) from audit_event")
        assert stats(total=2) == final
        assert sql("select count(*) from audit_event") == audits
        evidence.update(marker=marker, automaticTicket=automatic_id, finalStatistics=final, auditCount=audits,
                        realKeycloak=True, packagedArtifact=True, completed=True, responses=responses)
    args.evidence.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    Path(str(args.evidence) + ".statistics.json").write_text(json.dumps([
        response["body"] for response in responses if response["status"] == 200 and "/statistics?" in response["path"]]), encoding="utf-8")
    print("PASS R12", "restart" if args.verify_restart else "runtime", len(responses), "real HTTP responses")


if __name__ == "__main__":
    main()
