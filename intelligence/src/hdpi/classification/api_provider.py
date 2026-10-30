"""Opt-in development classification gateway for Gemini and OpenAI-compatible APIs.

The app supplies taxonomy/prompt policy. Responses use the same neutral protocol
as local models. No provider text, input or credentials are written to logs.
"""

import argparse
from hashlib import sha256
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import logging
import os
from pathlib import Path
import re
import socket
import time
from threading import BoundedSemaphore
import urllib.error
import urllib.parse
import urllib.request

from .decision_observation import decode_decision
from .protocols import ClassificationError, require
from .service import ClassificationInferenceService
from ..generation.errors import GenerationTimeout, GenerationUnavailable


def strict_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result)
            result[key] = value
        return result

    def finite(_):
        raise ClassificationError("Non-finite JSON rejected")

    return json.loads(raw, object_pairs_hook=unique, parse_constant=finite)


# Gemini hay trả 503 "high demand" chốc lát: thử lại ngắn thay vì bỏ nhãn của ticket
RETRY_STATUSES = (429, 500, 502, 503, 504)
RETRY_ATTEMPTS = 3
RETRY_BACKOFF_SECONDS = 1.0


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise GenerationUnavailable("Provider redirects are disabled")


class ApiProvider:
    def __init__(
        self,
        *,
        vendor,
        base_url,
        model,
        credential_file,
        policy,
        timeout=12,
        response_format="json_object",
        max_tokens=1024,
        allow_http=False
    ):
        require(vendor in ("gemini", "openai", "groq"))
        uri = urllib.parse.urlparse(base_url)
        require(
            uri.scheme == "https"
            or allow_http
            and uri.scheme == "http"
            and uri.hostname in ("127.0.0.1", "localhost", "host.docker.internal")
        )
        require(
            uri.hostname
            and not uri.username
            and not uri.password
            and not uri.query
            and not uri.fragment
        )
        require(re.fullmatch(r"[A-Za-z0-9_.:/-]{1,150}", model) is not None)
        require(
            response_format in ("json_schema", "json_object")
            and 1 <= timeout <= 15
            and 64 <= max_tokens <= 2048
        )
        self.vendor, self.base_url, self.model = vendor, base_url.rstrip("/"), model
        self.policy, self.timeout = policy, timeout
        self.credential_file = Path(credential_file)
        self.response_format, self.max_tokens = response_format, max_tokens
        self.slot = BoundedSemaphore(1)
        self.sleep, self.clock = time.sleep, time.monotonic
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirect()
        )
        self.read_credential()  # Fail configuration before accepting any ticket input.

    @property
    def model_digest(self):
        # Configuration identity, not a claim to pin a cloud model's immutable weights.
        return sha256(
            (self.vendor + "\n" + self.base_url + "\n" + self.model).encode()
        ).hexdigest()

    def read_credential(self):
        require(self.credential_file.stat().st_size <= 4096)
        key = self.credential_file.read_text("ascii").strip()
        require(8 <= len(key) <= 4096 and all(33 <= ord(char) <= 126 for char in key))
        return key

    def build_request(self, request):
        schema = {
            "type": "object",
            "additionalProperties": False,
            "required": ["decision", "classId"],
            "properties": {
                "decision": {"type": "string", "enum": ["PROPOSE", "ABSTAIN"]},
                "classId": {
                    "type": ["string", "null"],
                    "enum": [*request.allowed_classes, None],
                },
            },
        }
        data = json.dumps(
            dict(request.fields), ensure_ascii=False, separators=(",", ":")
        )
        instructions = (
            self.policy["instructions"]
            + '\nReturn JSON only: {"decision":"PROPOSE"|"ABSTAIN","classId":<allowed class or null>}.'
        )
        instructions += "\nAllowed classId values: " + json.dumps(
            list(request.allowed_classes)
        )
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.vendor == "gemini":
            url = (
                self.base_url
                + "/models/"
                + urllib.parse.quote(self.model, safe="")
                + ":generateContent"
            )
            headers["x-goog-api-key"] = self.read_credential()
            body = {
                "systemInstruction": {"parts": [{"text": instructions}]},
                "contents": [{"role": "user", "parts": [{"text": data}]}],
                "generationConfig": {
                    "maxOutputTokens": self.max_tokens,
                    "responseFormat": {
                        "text": {"mimeType": "APPLICATION_JSON", "schema": schema}
                    },
                },
            }
        else:
            url = self.base_url + "/chat/completions"
            headers["Authorization"] = "Bearer " + self.read_credential()
            response_format = (
                {"type": "json_object"}
                if self.response_format == "json_object"
                else {
                    "type": "json_schema",
                    "json_schema": {
                        "name": "ticket_classification",
                        "strict": True,
                        "schema": schema,
                    },
                }
            )
            body = {
                "model": self.model,
                "messages": [
                    {"role": "system", "content": instructions},
                    {"role": "user", "content": data},
                ],
                "stream": False,
                "max_completion_tokens": self.max_tokens,
                "response_format": response_format,
            }
        return urllib.request.Request(
            url, data=json.dumps(body, ensure_ascii=False).encode(), headers=headers
        )

    def send(self, request):
        """Gọi provider; quá tải tạm thời (429/5xx) thì thử lại trong cùng hạn timeout."""
        deadline = self.clock() + self.timeout
        for attempt in range(RETRY_ATTEMPTS):
            try:
                with self.opener.open(
                    self.build_request(request),
                    timeout=max(1, deadline - self.clock()),
                ) as response:
                    require(
                        response.status == 200
                        and response.headers.get_content_type() == "application/json"
                    )
                    return response.read(65537)
            except urllib.error.HTTPError as error:
                wait = RETRY_BACKOFF_SECONDS * (attempt + 1)
                last = attempt == RETRY_ATTEMPTS - 1
                if error.code not in RETRY_STATUSES or last or deadline - self.clock() < wait + 1:
                    raise
                logging.warning(
                    "Classification provider busy, retrying (vendor=%s, HTTP=%d)",
                    self.vendor,
                    error.code,
                )
                self.sleep(wait)
        raise GenerationUnavailable("Provider retries exhausted")

    def observe(self, request):
        if not self.slot.acquire(blocking=False):
            raise GenerationUnavailable("Provider is busy")
        try:
            raw = self.send(request)
            require(len(raw) <= 65536)
            outer = strict_json(raw)
            if self.vendor == "gemini":
                candidates = outer.get("candidates", [])
                require(
                    len(candidates) == 1 and candidates[0].get("finishReason") == "STOP"
                )
                parts = candidates[0].get("content", {}).get("parts", [])
                texts = [
                    part["text"]
                    for part in parts
                    if "text" in part and not part.get("thought", False)
                ]
                require(len(texts) == 1)
                text = texts[0]
            else:
                choices = outer.get("choices", [])
                require(len(choices) == 1 and choices[0].get("finish_reason") == "stop")
                message = choices[0].get("message", {})
                require(not message.get("refusal") and not message.get("tool_calls"))
                text = message.get("content")
            require(type(text) is str)
            return decode_decision(
                text.encode(), request, abstain_id=self.policy["abstainId"]
            )
        except (socket.timeout, TimeoutError):
            logging.warning(
                "Classification provider deadline exceeded (vendor=%s)", self.vendor
            )
            raise GenerationTimeout("Provider deadline exceeded") from None
        except urllib.error.HTTPError as error:
            logging.warning(
                "Classification provider rejected request (vendor=%s, HTTP=%d)",
                self.vendor,
                error.code,
            )
            raise GenerationUnavailable("Provider rejected the request") from None
        except urllib.error.URLError:
            raise GenerationUnavailable("Provider connection unavailable") from None
        except (ValueError, KeyError, TypeError):
            logging.warning(
                "Classification provider output failed validation (vendor=%s)",
                self.vendor,
            )
            raise ClassificationError("Provider output rejected") from None
        finally:
            self.slot.release()


class ApiHandler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def authorized(self):
        values = self.headers.get_all("Authorization", [])
        return (
            len(values) == 1
            and values[0].isascii()
            and hmac.compare_digest(values[0], "Bearer " + self.server.token)
        )

    def reply(self, status, value):
        body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self.authorized():
            self.reply(403, {"code": "CLASSIFICATION_REJECTED"})
        elif self.path == "/health/ready":
            self.reply(200, {"ready": True, "upstreamVerified": False})
        else:
            self.reply(404, {"code": "CLASSIFICATION_ROUTE_NOT_FOUND"})

    def do_POST(self):
        if not self.authorized():
            self.reply(403, {"code": "CLASSIFICATION_REJECTED"})
            return
        advice = self.path == "/internal/intelligence/v1/assistance"
        if self.path != "/internal/intelligence/v1/classify" and not advice:
            self.reply(404, {"code": "CLASSIFICATION_ROUTE_NOT_FOUND"})
            return
        status = 502
        try:
            self.connection.settimeout(3)
            lengths = self.headers.get_all("Content-Length", [])
            require(
                len(lengths) == 1 and lengths[0].isascii() and lengths[0].isdecimal()
            )
            size = int(lengths[0])
            require(
                0 < size <= (32768 if advice else 16384)
                and self.headers.get("Transfer-Encoding") is None
            )
            require(self.headers.get("Content-Type") == "application/json")
            raw = self.rfile.read(size)
            require(len(raw) == size)
            if advice:
                if self.server.assistance is None:
                    raise GenerationUnavailable("Assistance is disabled")
                result = self.server.assistance.answer(strict_json(raw))
            else:
                result = self.server.service.infer(strict_json(raw))
            self.reply(200, result)
            return
        except GenerationTimeout:
            status = 504
        except GenerationUnavailable:
            status = 503
        except Exception:
            pass
        self.reply(status, {"code": "CLASSIFICATION_REJECTED"})


class ApiServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 4

    def __init__(self, address, *, service, token, assistance=None):
        self.service, self.token = service, token
        self.assistance = assistance
        super().__init__(address, ApiHandler)

    def handle_error(self, *_args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--health", action="store_true")
    args = parser.parse_args()
    token = Path(os.environ["AI_CLASSIFICATION_TOKEN_FILE"]).read_text("ascii").strip()
    require(32 <= len(token) <= 128 and token.isascii())
    if args.health:
        request = urllib.request.Request(
            "http://127.0.0.1:8003/health/ready",
            headers={"Authorization": "Bearer " + token},
        )
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
            request, timeout=2
        ) as response:
            require(strict_json(response.read(4096)).get("ready") is True)
        return
    require(os.environ.get("AI_API_ENABLED") == "1")
    raw = Path(os.environ["AI_API_POLICY_FILE"]).read_bytes()
    policy = strict_json(raw)
    provider = ApiProvider(
        vendor=os.environ["AI_API_PROVIDER"],
        base_url=os.environ["AI_API_BASE_URL"],
        model=os.environ["AI_API_MODEL"],
        credential_file=os.environ["AI_API_KEY_FILE"],
        policy=policy,
        response_format=os.environ.get("AI_API_RESPONSE_FORMAT", "json_object"),
        max_tokens=int(os.environ.get("AI_API_MAX_TOKENS", "1024")),
    )
    require(provider.model_digest == os.environ["AI_API_MODEL_DIGEST"])
    require(sha256(raw).hexdigest() == os.environ["AI_API_POLICY_DIGEST"])
    service = ClassificationInferenceService(
        model_digest=provider.model_digest,
        policy_digest=sha256(raw).hexdigest(),
        allowed_classes=policy["allowedClasses"],
        abstain_id=policy["abstainId"],
        observe=provider.observe,
    )
    assistance = None
    if os.environ.get("AI_API_ASSISTANCE_ENABLED") == "1":
        from ..advisory_api import ApiAssistanceProvider

        advice_raw = Path(os.environ["AI_API_ASSISTANCE_POLICY_FILE"]).read_bytes()
        require(
            sha256(advice_raw).hexdigest()
            == os.environ["AI_API_ASSISTANCE_POLICY_DIGEST"]
        )
        assistance = ApiAssistanceProvider(
            vendor=provider.vendor,
            base_url=provider.base_url,
            model=provider.model,
            credential_file=provider.credential_file,
            policy=strict_json(advice_raw),
            max_tokens=int(os.environ.get("AI_API_ASSISTANCE_MAX_TOKENS", "2048")),
        )
    with ApiServer(
        ("0.0.0.0", 8003), service=service, token=token, assistance=assistance
    ) as server:
        server.serve_forever(poll_interval=0.2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        raise SystemExit(
            "Classification API configuration or readiness failed"
        ) from None
