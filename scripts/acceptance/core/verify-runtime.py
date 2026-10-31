import argparse
import base64
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import uuid


parser = argparse.ArgumentParser(description="Independent HTTP client: synthetic room-loan application")
parser.add_argument("mode", choices=["seed", "verify", "resume", "upload-disabled"])
parser.add_argument("state", type=Path)
parser.add_argument("--core-url", default="http://localhost:9080")
parser.add_argument("--identity-url", default="http://localhost:8081")
parser.add_argument("--env-file", type=Path, default=Path(".env"))
args = parser.parse_args()
for url in (args.core_url, args.identity_url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1") or parsed.username or parsed.password:
        parser.error("Only loopback acceptance services are allowed")
settings = dict(line.split("=", 1) for line in args.env_file.read_text(encoding="utf-8-sig").splitlines() if "=" in line and not line.startswith("#"))
password = settings["DEMO_PASSWORD"]
if args.mode == "seed":
    data = urllib.parse.urlencode(dict(grant_type="password", client_id="admin-cli", username="admin", password=settings["KC_ADMIN_PASSWORD"])).encode()
    with urllib.request.urlopen(args.identity_url + "/realms/master/protocol/openid-connect/token", data, timeout=30) as response:
        admin_token = json.load(response)["access_token"]
    client, = (client for client in json.loads(Path("infra/keycloak/realm-template.json").read_text(encoding="utf-8"))["clients"]
               if client["clientId"] == "core-probe")
    assert client["publicClient"] and client["directAccessGrantsEnabled"]
    client.update(clientId="room-loan-probe", description="Acceptance-only second application; not a browser login design")
    request = urllib.request.Request(args.identity_url + "/admin/realms/dx-lab/clients", json.dumps(client).encode(),
                                     {"Authorization": "Bearer " + admin_token, "Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        assert response.status == 201
tokens, subjects, responses = {}, {}, []
for username in ["requester1", "agent1", "coordinator1", "requester2", "agent6", "coordinator2", "admin1"]:
    data = urllib.parse.urlencode(dict(grant_type="password", client_id="room-loan-probe", username=username, password=password)).encode()
    with urllib.request.urlopen(args.identity_url + "/realms/dx-lab/protocol/openid-connect/token", data, timeout=30) as response:
        tokens[username] = json.load(response)["access_token"]
        payload = tokens[username].split(".")[1]
        assert json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))["azp"] == "room-loan-probe"


def call(method, path, user, expected, body=None, raw=None, headers=None):
    request_headers = dict(headers or {})
    if user:
        request_headers["Authorization"] = "Bearer " + tokens[user]
    if body is not None:
        raw = json.dumps(body).encode()
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(args.core_url + "/api/core/v1" + path, raw, request_headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        content = response.read()
        assert response.status == expected, f"{method} {path}: expected {expected}, got {response.status}: {content[:500]!r}"
        assert response.headers.get("X-Request-ID"), f"Missing request ID: {path}"
        if response.headers.get_content_type() in ["application/json", "application/problem+json"]:
            responses.append(dict(method=method, path=path, status=expected, bodyJson=content.decode()))
            result = json.loads(content)
            if expected >= 400:
                assert response.headers.get_content_type() == "application/problem+json"
                assert result["requestId"] and result["code"]
        else:
            result = content
        print(f"PASS {method} {path} -> {expected}")
        return result, response.headers


base = "/workspaces/lab-a"
for username in tokens:
    identity, _ = call("GET", "/me", username, 200)
    subjects[username] = identity["sub"]


def upload(expected, name="room-loan.txt", media="text/plain"):
    boundary = uuid.uuid4().hex
    payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
               f"Content-Type: {media}\r\n\r\nSynthetic room loan; CC0-1.0.\r\n--{boundary}--\r\n").encode()
    return call("POST", base + "/files", "agent1", expected, raw=payload,
                headers={"Content-Type": "multipart/form-data; boundary=" + boundary})[0]


def start(user="requester1"):
    return call("POST", base + "/processes/equipment-approval/instances", user, 201,
                dict(businessKey="room-loan:" + uuid.uuid4().hex, variables={"title": "Shared classroom projector loan"}))[0]


if args.mode == "seed":
    call("GET", "/me", None, 401, headers={"X-User-Id": "admin", "X-Roles": "coordinator", "X-Workspace": "lab-a"})
    call("GET", "/me", None, 401, headers={"Authorization": "Bearer not-a-jwt"})
    call("GET", base + "/metadata", "agent6", 403)
    call("GET", base + "/metadata", "admin1", 403)
    call("GET", base + "/audit", "agent1", 403)
    call("GET", base + "/audit", "coordinator2", 403)
    call("GET", base + "/audit", "admin1", 403)
    for reserved in [{"workspace": "lab-b"}, {"initiator": subjects["coordinator1"]}, {"outcome_approve": "approved"}, {"title": {"expression": "evil"}}]:
        call("POST", base + "/processes/equipment-approval/instances", "requester1", 400,
             dict(businessKey=uuid.uuid4().hex, variables=reserved))
    upload(400, name="../escape.txt")
    upload(415, name="fake.png", media="image/png")
    file = upload(201)
    metadata_input = dict(title="Room loan evidence", source="app:room-loan; synthetic", version="1", usageRights="CC0-1.0", fileId=file["id"])
    call("POST", base + "/metadata", "coordinator1", 404, metadata_input)
    call("POST", base + "/metadata", "agent1", 400, dict(metadata_input, owner=subjects["coordinator1"]))
    call("POST", base + "/metadata", "agent1", 404, dict(metadata_input, fileId=str(uuid.uuid4())))
    metadata, _ = call("POST", base + "/metadata", "agent1", 201, metadata_input)
    for kind, identifier in [("metadata", metadata["id"]), ("files", file["id"])]:
        call("GET", f"/workspaces/lab-b/{kind}/{identifier}", "agent6", 404)
        call("GET", f"{base}/{kind}/{identifier}", "admin1", 403)
    own = start("coordinator1")
    own_path = base + "/instances/" + own["id"]
    own_tasks, _ = call("GET", own_path + "/tasks", "coordinator1", 200)
    call("POST", own_path + "/tasks/" + own_tasks[0]["id"] + "/claim", "coordinator1", 403)
    process = start()
    process_path = base + "/instances/" + process["id"]
    tasks, _ = call("GET", process_path + "/tasks", "requester1", 200)
    task_path = process_path + "/tasks/" + tasks[0]["id"]
    call("POST", own_path + "/tasks/" + tasks[0]["id"] + "/claim", "coordinator1", 404)
    call("POST", task_path + "/claim", "coordinator2", 403)
    call("POST", task_path + "/claim", "agent1", 403)
    call("POST", task_path + "/complete", "coordinator1", 403, dict(expectedTaskKey="approve", outcome="approved"))
    call("POST", task_path + "/claim", "coordinator1", 200)
    call("POST", task_path + "/complete", "coordinator1", 400, dict(expectedTaskKey="approve", outcome="forged"))
    call("POST", task_path + "/complete", "coordinator1", 204, dict(expectedTaskKey="approve", outcome="approved", comment="Room-loan client; no Core changes"))
    tasks, _ = call("GET", process_path + "/tasks", "requester1", 200)
    assert len(tasks) == 1 and tasks[0]["key"] == "handover"
    state = dict(subjects=subjects, file=file, metadata=metadata, process=process, pendingTask=tasks[0])
    args.state.parent.mkdir(parents=True, exist_ok=True)
    args.state.write_text(json.dumps(state, indent=2))
else:
    state = json.loads(args.state.read_text())
    assert subjects == state["subjects"], "Keycloak subjects changed across restart/restore"
    if args.mode == "upload-disabled":
        upload(403)
    else:
        call("GET", base + "/metadata/" + state["metadata"]["id"], "requester1", 200)
        metadata, _ = call("GET", base + "/metadata/" + state["metadata"]["id"], "agent1", 200)
        assert metadata == state["metadata"], "Metadata/provenance changed"
        call("GET", base + "/files/" + state["file"]["id"], "requester1", 200)
        file, _ = call("GET", base + "/files/" + state["file"]["id"], "agent1", 200)
        assert file == state["file"], "File description changed"
        call("GET", base + "/files/" + file["id"] + "/content", "requester1", 403)
        content, headers = call("GET", base + "/files/" + file["id"] + "/content", "agent1", 200)
        assert hashlib.sha256(content).hexdigest() == file["checksum"]
        assert headers["X-Content-Type-Options"] == "nosniff" and headers["Content-Disposition"].startswith("attachment;")
        process_path = base + "/instances/" + state["process"]["id"]
        tasks, _ = call("GET", process_path + "/tasks", "requester1", 200)
        assert len(tasks) == 1 and tasks[0]["id"] == state["pendingTask"]["id"] and tasks[0]["key"] == "handover"
        history, _ = call("GET", process_path + "/history", "requester1", 200)
        assert not history["ended"] and history["steps"][0]["outcome"] == "approved"
        if args.mode == "resume":
            task_path = process_path + "/tasks/" + tasks[0]["id"]
            call("POST", task_path + "/claim", "agent1", 200)
            call("POST", task_path + "/complete", "agent1", 204, dict(expectedTaskKey="handover", outcome="delivered"))
            history, _ = call("GET", process_path + "/history", "requester1", 200)
            assert history["ended"] and len(history["steps"]) == 2

args.state.with_name(args.mode + "-http.json").write_text(json.dumps(responses, indent=2))
print(f"PASS independent room-loan client: {args.mode}")
