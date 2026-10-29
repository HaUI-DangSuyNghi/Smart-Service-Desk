"""Bounded API question answering; no fabricated retrieval evidence or tool execution."""

import json
import logging
import socket
import urllib.error
import urllib.parse
import urllib.request
from uuid import UUID

from .classification.api_provider import ApiProvider, strict_json
from .classification.protocols import ClassificationError, require
from .generation.errors import GenerationTimeout, GenerationUnavailable


class ApiAssistanceProvider(ApiProvider):
    def build_advice_request(self, request):
        data = json.dumps(
            {"question": request["question"], "ticket": request["context"]},
            ensure_ascii=False,
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
                "systemInstruction": {"parts": [{"text": self.policy["instructions"]}]},
                "contents": [{"role": "user", "parts": [{"text": data}]}],
                "generationConfig": {"maxOutputTokens": self.max_tokens},
            }
        else:
            url = self.base_url + "/chat/completions"
            headers["Authorization"] = "Bearer " + self.read_credential()
            body = {
                "model": self.model,
                "messages": [
                    {"role": "system", "content": self.policy["instructions"]},
                    {"role": "user", "content": data},
                ],
                "stream": False,
                "max_completion_tokens": self.max_tokens,
            }
        return urllib.request.Request(
            url, data=json.dumps(body, ensure_ascii=False).encode(), headers=headers
        )

    def answer(self, request):
        require(
            type(request) is dict
            and set(request) == {"contractVersion", "requestId", "question", "context"}
        )
        require(request["contractVersion"] == "advisory@0.1.0")
        require(
            type(request["requestId"]) is str
            and str(UUID(request["requestId"])) == request["requestId"]
        )
        require(
            type(request["question"]) is str
            and bool(request["question"].strip())
            and len(request["question"]) <= 2000
        )
        require(
            type(request["context"]) is dict
            and set(request["context"]) == {"title", "description"}
        )
        for value in [request["question"], *request["context"].values()]:
            require(type(value) is str and len(value) <= 4000)
            require(
                all(
                    not (ord(char) < 32 or 127 <= ord(char) <= 159) or char in "\n\r\t"
                    for char in value
                )
            )
        if not self.slot.acquire(blocking=False):
            raise GenerationUnavailable("Provider is busy")
        try:
            with self.opener.open(
                self.build_advice_request(request), timeout=self.timeout
            ) as response:
                require(
                    response.status == 200
                    and response.headers.get_content_type() == "application/json"
                )
                require(response.headers.get("Content-Encoding") is None)
                raw = response.read(65537)
            require(len(raw) <= 65536)
            outer = strict_json(raw)
            if self.vendor == "gemini":
                candidates = outer.get("candidates", [])
                require(
                    len(candidates) == 1 and candidates[0].get("finishReason") == "STOP"
                )
                parts = candidates[0].get("content", {}).get("parts", [])
                require(
                    parts
                    and all(
                        set(part) <= {"text", "thought", "thoughtSignature"}
                        and type(part.get("text")) is str
                        for part in parts
                    )
                )
                text = "\n".join(
                    part["text"] for part in parts if not part.get("thought", False)
                )
            else:
                choices = outer.get("choices", [])
                require(len(choices) == 1 and choices[0].get("finish_reason") == "stop")
                message = choices[0].get("message", {})
                require(
                    not message.get("tool_calls")
                    and not message.get("function_call")
                    and not message.get("refusal")
                )
                text = message.get("content")
            require(type(text) is str and 0 < len(text.strip()) <= 16000)
            require(
                all(
                    not (ord(char) < 32 or 127 <= ord(char) <= 159) or char in "\n\r\t"
                    for char in text
                )
            )
            return {
                "contractVersion": "advisory@0.1.0",
                "requestId": request["requestId"],
                "answer": text.strip(),
            }
        except (socket.timeout, TimeoutError):
            raise GenerationTimeout("Provider deadline exceeded") from None
        except urllib.error.HTTPError as error:
            logging.warning(
                "Assistance provider rejected request (vendor=%s, HTTP=%d)",
                self.vendor,
                error.code,
            )
            raise GenerationUnavailable("Provider rejected the request") from None
        except urllib.error.URLError:
            raise GenerationUnavailable("Provider connection unavailable") from None
        except (ValueError, KeyError, TypeError):
            raise ClassificationError("Provider output rejected") from None
        finally:
            self.slot.release()
