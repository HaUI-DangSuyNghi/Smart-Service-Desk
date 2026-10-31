"""Exercise the localhost app with an owned synthetic fixture; never prints credentials.

Requires the demo realm and scripts/start.ps1 -Model. Creates a test ticket.
"""

import argparse
import base64
import hashlib
import http.cookiejar
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[4]
AUTH = "http://localhost:8081"
WEB = "http://localhost:5173"
API = "http://localhost:9080/api/apps/servicedesk/v1"


class LoginForm(HTMLParser):
    action = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form" and attrs.get("id") == "kc-form-login":
            self.action = attrs.get("action")


class LocalCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    def return_ok_secure(self, cookie, request):
        # Browsers treat loopback HTTP as a secure context; stdlib CookieJar does not.
        if urllib.parse.urlparse(request.full_url).netloc == "localhost:8081":
            return True
        return super().return_ok_secure(cookie, request)


def login(username, password):
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}),
        urllib.request.HTTPCookieProcessor(
            http.cookiejar.CookieJar(policy=LocalCookiePolicy())
        ),
    )
    verifier = secrets.token_urlsafe(48)
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest())
        .rstrip(b"=")
        .decode()
    )
    state = secrets.token_urlsafe(24)
    callback = WEB + "/auth/callback"
    query = urllib.parse.urlencode(
        dict(
            client_id="dx-web",
            response_type="code",
            scope="openid",
            redirect_uri=callback,
            state=state,
            code_challenge=challenge,
            code_challenge_method="S256",
        )
    )
    with opener.open(
        AUTH + "/realms/dx-lab/protocol/openid-connect/auth?" + query, timeout=20
    ) as response:
        form = LoginForm()
        form.feed(response.read().decode())
    if not form.action or urllib.parse.urlparse(form.action).netloc != "localhost:8081":
        raise RuntimeError("Local login form unavailable")
    body = urllib.parse.urlencode(
        dict(username=username, password=password, credentialId="")
    ).encode()
    with opener.open(form.action, data=body, timeout=20) as response:
        location = urllib.parse.urlparse(response.url)
        params = urllib.parse.parse_qs(location.query)
    if (
        location.netloc != "localhost:5173"
        or params.get("state") != [state]
        or not params.get("code")
    ):
        raise RuntimeError("Demo login failed: " + username)
    body = urllib.parse.urlencode(
        dict(
            client_id="dx-web",
            grant_type="authorization_code",
            code=params["code"][0],
            redirect_uri=callback,
            code_verifier=verifier,
        )
    ).encode()
    with opener.open(
        AUTH + "/realms/dx-lab/protocol/openid-connect/token", data=body, timeout=20
    ) as response:
        return json.load(response)["access_token"]


def request(path, token=None, body=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = (
        None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    )
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, headers=headers, data=data)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, None


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--expect-unavailable", action="store_true")
    parser.add_argument("--retry-ticket", type=uuid.UUID)
    args = parser.parse_args()
    config = dict(
        line.split("=", 1)
        for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    token = login("requester1", config["DEMO_PASSWORD"])
    status_path = "/workspaces/lab-a/intelligence/status"
    require(
        request(status_path)[0] == 401, "Anonymous status must require authentication"
    )
    code, status = request(status_path, token)
    require(
        code == 200 and status["modelEnabled"] and status["mode"] == "LOCAL_DEV",
        "Backend is not in local model mode",
    )
    base = "/workspaces/lab-a/tickets"
    if args.retry_ticket:
        ticket_id = str(args.retry_ticket)
        code, queued = request(base + "/" + ticket_id + "/assessment/retry", token, {})
        require(
            code == 200 and queued["status"] == "PENDING",
            "Failed advice was not requeued",
        )
    else:
        code, ticket = request(
            base,
            token,
            dict(
                clientRequestId=str(uuid.uuid4()),
                title="Máy 15 phòng 304 A1 không lên nguồn",
                description=(
                    "Một máy bị ảnh hưởng; buổi thi bắt đầu sau 10 phút."
                    if args.expect_unavailable
                    else "Máy này tôi đang cần để làm bài thi gấp trong 15 phút nữa"
                ),
            ),
        )
        require(code == 201, "Synthetic ticket creation failed")
        ticket_id = ticket["id"]
    path = base + "/" + ticket_id
    deadline = time.monotonic() + 100
    stored = None
    while time.monotonic() < deadline:
        code, ticket = request(path, token)
        require(code == 200, "Created ticket must remain readable")
        stored = ticket.get("assessment")
        if stored and stored["status"] != "PENDING":
            break
        time.sleep(1)
    require(
        stored is not None and stored["status"] != "PENDING",
        "Assessment did not finish",
    )
    proof = {"ticketId": ticket_id, "mode": status["mode"], "authenticated": True}
    if args.expect_unavailable:
        require(
            stored["status"] == "READY"
            and stored["result"]["cooperation"]["modelStatus"] == "UNAVAILABLE",
            "Expected explicit model outage with rules preserved",
        )
        require(
            stored["result"]["cooperation"]["rulesStatus"] != "UNAVAILABLE",
            "Rules must remain active during a model outage",
        )
        require(
            stored["result"]["category"] == "HARDWARE"
            and stored["result"]["priority"] == "HIGH",
            "Rules must retain valid classification and priority during a model outage",
        )
        proof.update(
            result="OUTAGE_HANDLED",
            rulesStatus=stored["result"]["cooperation"]["rulesStatus"],
            category=stored["result"]["category"],
            priority=stored["result"]["priority"],
        )
    else:
        require(
            stored["status"] == "READY",
            "Model processing failed: " + str(stored.get("errorCode")),
        )
        result = stored["result"]
        require(
            result["engine"] == "hybrid" and result["category"] == "HARDWARE",
            "Hybrid pipeline must classify this physical-power fixture",
        )
        require(
            result["cooperation"]["modelStatus"] == "AVAILABLE"
            and result["cooperation"]["rulesStatus"] != "UNAVAILABLE",
            "Both model and rules must execute",
        )
        require(
            result["priority"] == "HIGH"
            and result["impact"] == "I1"
            and result["urgency"] == "U4",
            "Numbered device/deadline interpretation is incorrect",
        )
        require(result["needsReview"] is True, "Advice must still require human review")
        for _ in range(3):
            require(
                request(path, token)[1]["assessment"] == stored,
                "GET changed stored model advice",
            )
        require(
            request(path + "/assessment/retry", token, {})[0] == 409,
            "Completed model advice cannot be repeatedly requeued",
        )
        foreign = login("requester2", config["DEMO_PASSWORD"])
        require(
            request(path, foreign)[0] in (403, 404),
            "Cross-workspace ticket access must be denied",
        )
        require(
            request(path + "/assessment/retry", foreign, {})[0] in (403, 404),
            "Cross-workspace retries must be denied",
        )
        proof.update(
            result="PASS",
            engine=result["engine"],
            category=result["category"],
            priority=result["priority"],
            impact=result["impact"],
            urgency=result["urgency"],
            stableReads=3,
            crossWorkspaceDenied=True,
            cooperation=result["cooperation"],
        )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(proof))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, urllib.error.URLError, KeyError) as error:
        # No URLs, responses, tokens or passwords in errors.
        print(
            "Local model verification failed: "
            + (str(error) if isinstance(error, RuntimeError) else type(error).__name__)
        )
        raise SystemExit(1) from None
