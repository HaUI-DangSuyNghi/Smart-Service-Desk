"""R3 packaged-backend acceptance; requires an isolated real Keycloak/PostgreSQL stack."""
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
    parser.add_argument("--keycloak", default="http://localhost:8081")
    parser.add_argument("--postgres", required=True, help="Disposable acceptance database container only")
    parser.add_argument("--backend", required=True, help="Packaged-artifact container to restart")
    parser.add_argument("--evidence", type=Path, required=True)
    arguments = parser.parse_args()
    settings = dict(line.split("=", 1) for line in Path(".env").read_text(encoding="utf-8-sig").splitlines()
                    if "=" in line and not line.startswith("#"))
    evidence = dict(realKeycloak=True, packagedArtifact=True, responses=[], checks=[], completed=False)

    def request(url, method="GET", bearer=None, body=None, expected=200, form=False):
        headers = {"Content-Type": "application/x-www-form-urlencoded" if form else "application/json"}
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        data = None if body is None else (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
        try:
            response = urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=30)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            status, raw = response.status, response.read().decode()
        result = json.loads(raw) if raw else None
        if url.startswith(arguments.base):
            evidence["responses"].append(dict(method=method, path=url[len(arguments.base):], status=status, body=result))
        assert status == expected, (method, url, expected, status, result)
        return result

    def token(username, realm="dx-lab", client="core-probe", password=None):
        return request(arguments.keycloak + "/realms/" + realm + "/protocol/openid-connect/token", "POST",
                       body=dict(grant_type="password", client_id=client, username=username,
                                 password=password or settings["DEMO_PASSWORD"]), form=True)["access_token"]

    def subject(bearer):
        payload = bearer.split(".")[1]
        return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))["sub"]

    def sql(statement):
        return subprocess.run(["docker", "exec", "-i", arguments.postgres, "psql", "-U", "core", "-d", "core",
                               "-At", "-v", "ON_ERROR_STOP=1"], input=statement, text=True,
                              capture_output=True, check=True).stdout.strip()

    def check(name):
        evidence["checks"].append(name)
        print("PASS", name, flush=True)

    def call(method, path, bearer, body=None, expected=200):
        return request(arguments.base + path, method, bearer, body, expected)

    requester, agent, coordinator, foreign = (token(name) for name in ("user1", "user2", "user3", "user5"))
    agent_id = subject(agent)
    work = "/api/hpdi/v1/workspaces/lab-a"
    tickets = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets"
    schedule_path = work + "/schedules/" + agent_id

    def schedule(seconds=1200, expired=False):
        current = call("GET", schedule_path, coordinator)
        now = datetime.now(timezone.utc)
        start, end = (now - timedelta(hours=2), now - timedelta(hours=1)) if expired else (now - timedelta(minutes=1), now + timedelta(seconds=seconds))
        return call("PUT", schedule_path, coordinator, dict(version=current["version"], enabled=True,
                    shifts=[dict(startsAt=start.isoformat(), endsAt=end.isoformat(), timezone="Asia/Ho_Chi_Minh")]))

    def create():
        return call("POST", tickets, requester, dict(clientRequestId=str(uuid.uuid4()), title="R3 runtime acceptance",
                    description="Synthetic isolated R3 scheduling and handover probe"), 201)

    def review(ticket, assignee):
        return call("POST", tickets + "/" + ticket["id"] + "/review", coordinator,
                    dict(category="OTHER", priority="HIGH", assignee=assignee, estimateMinutes=60, reason="R3 acceptance"))

    try:
        for attempt in range(90):
            try:
                request(arguments.base + "/actuator/health")
                break
            except (OSError, AssertionError):
                time.sleep(1)
        else:
            raise AssertionError("Packaged backend did not become healthy")
        evidence["postgresVersion"] = sql("show server_version")
        assert evidence["postgresVersion"].startswith("16.6"), "PostgreSQL 16.6 required"
        evidence["flowableSchemaVersion"] = sql("select value_ from act_ge_property where name_='schema.version'")
        schedule()
        assert call("GET", work + "/schedules/me", agent)["subject"] == agent_id
        call("PUT", schedule_path, requester, dict(version=1, enabled=True, shifts=[]), 403)
        call("GET", schedule_path, foreign, expected=403)
        check("coordinator-create-agent-self-read-requester-denial-workspace-isolation")

        admin = token("admin", "master", "admin-cli", settings["KC_ADMIN_PASSWORD"])
        admin_path = arguments.keycloak + "/admin/realms/dx-lab/users/" + subject(coordinator) + "/groups"
        groups = request(admin_path, bearer=admin)
        group = next(item for item in groups if item["path"] == "/workspaces/lab-a/coordinator")
        membership = admin_path + "/" + group["id"]
        request(membership, "DELETE", admin, expected=204)
        try:
            current = call("GET", work + "/schedules/me", agent)
            call("PUT", schedule_path, coordinator, dict(version=current["version"], enabled=True, shifts=[]), 403)
        finally:
            request(membership, "PUT", admin, expected=204)
        check("revoked-coordinator-stale-real-token-denied")

        call("PUT", work + "/duty/me", agent, dict(onDuty=True))
        schedule()
        preview_ticket = create()
        before = datetime.now(timezone.utc)
        suggestion = call("GET", tickets + "/" + preview_ticket["id"] + "/assignment-suggestion?category=OTHER&priority=HIGH", coordinator)
        after = datetime.now(timezone.utc)
        effective = call("GET", schedule_path + "/effective-duty", agent)
        end = datetime.fromisoformat(effective["shiftEnd"].replace("Z", "+00:00"))
        candidate = next(item for item in suggestion["candidates"] if item["subject"] == agent_id)
        assert int((end-after).total_seconds()//60) <= candidate["shiftRemainingMinutes"] <= int((end-before).total_seconds()//60)
        assert "ESTIMATE_EXCEEDS_SHIFT" in suggestion["reason"]
        assigned = review(preview_ticket, agent_id)
        check("manual-on-in-shift-assignment-exact-remaining-minutes-et-warning")

        unassigned = review(create(), None)
        assign_path = work + "/tasks/" + unassigned["taskId"] + "/assign"
        call("PUT", work + "/duty/me", agent, dict(onDuty=False))
        assert not call("GET", schedule_path + "/effective-duty", agent)["onDuty"]
        call("POST", assign_path, coordinator, dict(assignee=agent_id), 409)
        call("PUT", work + "/duty/me", agent, dict(onDuty=True))
        schedule(expired=True)
        call("POST", assign_path, coordinator, dict(assignee=agent_id), 409)
        check("manual-off-precedence-outside-shift-new-assignment-denied")

        schedule(seconds=6)
        task_path = work + "/tasks/" + assigned["taskId"]
        call("POST", task_path + "/start", agent)
        time.sleep(2)
        prior_duty_alert_ids = set(sql("select id from hpdi_alert_event where type='ASSIGNEE_OFF_DUTY' and task_id='"+assigned["taskId"]+"'").splitlines())
        time.sleep(5)
        retained = call("GET", tickets + "/" + assigned["id"], coordinator)
        assert retained["workState"] == "IN_PROGRESS"
        assert not call("GET", schedule_path + "/effective-duty", agent)["onDuty"]
        check("natural-shift-expiry-does-not-stop-in-progress-work")

        def duty_alerts():
            inbox = call("GET", work + "/alerts?size=100", coordinator)
            return [item for item in inbox["items"] if item["type"] == "ASSIGNEE_OFF_DUTY" and item["taskId"] == assigned["taskId"]
                    and item["id"] not in prior_duty_alert_ids]

        for attempt in range(35):
            alerts = duty_alerts()
            if alerts:
                break
            time.sleep(1)
        assert len(alerts) == 1, alerts
        time.sleep(3)
        assert len(duty_alerts()) == 1
        event_id = alerts[0]["id"]
        recipients = sql("select subject from hpdi_alert_recipient where event_id='" + str(uuid.UUID(event_id)) + "' order by subject").splitlines()
        assert subject(coordinator) in recipients and subject(requester) not in recipients and subject(foreign) not in recipients
        check("real-service-identity-persistent-duty-alert-recipient-and-dedup")

        previous = call("GET", schedule_path, coordinator)
        sql("create function r3_reject_audit() returns trigger language plpgsql as $$ begin if new.action='human.schedule.changed' then raise exception 'R3 audit rollback probe' using errcode='23514'; end if; return new; end $$; create trigger r3_reject_audit before insert on audit_event for each row execute function r3_reject_audit();")
        try:
            call("PUT", schedule_path, coordinator, dict(version=previous["version"], enabled=False, shifts=[]), 409)
            assert call("GET", schedule_path, coordinator) == previous
        finally:
            sql("drop trigger r3_reject_audit on audit_event; drop function r3_reject_audit();")
        check("runtime-audit-failure-rolls-back-schedule-and-version")

        previous = schedule(seconds=15)
        call("POST", assign_path, coordinator, dict(assignee=agent_id))
        restart_boundary = datetime.fromisoformat(previous["shifts"][0]["endsAt"].replace("Z", "+00:00"))

        snapshot_query = "select md5(string_agg(row_to_json(snapshot)::text,',' order by row_to_json(snapshot)::text)) from (select * from {} where {}) snapshot"
        conditions = {"hpdi_agent_schedule": "subject='"+agent_id+"'", "hpdi_agent_shift": "subject='"+agent_id+"'",
                      "hpdi_agent_duty": "subject='"+agent_id+"'", "audit_event": "action='human.schedule.changed'"}
        def snapshot():
            return {table: sql(snapshot_query.format(table, where)) for table, where in conditions.items()}
        prior = snapshot()
        evidence["persistedStoreDigests"] = prior
        evidence["restartShiftBoundary"] = restart_boundary.isoformat()
        subprocess.run(["docker", "stop", arguments.backend], check=True, capture_output=True)
        time.sleep(max(0, (restart_boundary-datetime.now(timezone.utc)).total_seconds())+1)
        subprocess.run(["docker", "start", arguments.backend], check=True, capture_output=True)
        for attempt in range(90):
            try:
                request(arguments.base + "/actuator/health")
                break
            except (OSError, AssertionError):
                time.sleep(1)
        else:
            raise AssertionError("Packaged backend did not recover after restart")
        assert snapshot() == prior
        assert call("GET", schedule_path, coordinator) == previous
        assert sql("select count(*) from hpdi_alert_event where id='"+str(uuid.UUID(event_id))+"'") == "1"
        assert not call("GET", schedule_path + "/effective-duty", agent)["onDuty"]
        for attempt in range(35):
            replayed = call("GET", work + "/alerts?size=100", coordinator)
            replay_alerts = [item for item in replayed["items"] if item["type"] == "ASSIGNEE_OFF_DUTY" and item["taskId"] == unassigned["taskId"]]
            if replay_alerts:
                break
            time.sleep(1)
        assert len(replay_alerts) == 1
        time.sleep(3)
        assert sql("select count(*) from hpdi_alert_event where type='ASSIGNEE_OFF_DUTY' and task_id='"+unassigned["taskId"]+"'") == "1"
        check("packaged-backend-restart-across-shift-boundary-persists-stores-and-replays-worker-once")

        call("POST", task_path + "/release", agent, dict(reason="HANDOVER", note="End of shift: retain diagnostics and transfer ownership"), 204)
        handed = call("GET", tickets + "/" + assigned["id"], coordinator)
        assert handed["state"] == "AWAITING_REVIEW" and handed["process"] == assigned["process"]
        assert sql("select count(*) from servicedesk_work_release where task_id='"+assigned["taskId"]+"'") == "1"
        check("r6-handover-retains-process-and-durable-history")
        evidence["completed"] = True
    finally:
        arguments.evidence.parent.mkdir(parents=True, exist_ok=True)
        arguments.evidence.write_text(json.dumps(evidence, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
