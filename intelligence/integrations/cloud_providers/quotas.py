from datetime import datetime, timezone
from threading import Lock

from .contracts import CloudFailure


class QuotaGovernor:
    """Single-process DEV budgets; conservative reservation, no retries/refunds."""

    def __init__(self, clock=None):
        self.clock = clock or (lambda: datetime.now(timezone.utc).timestamp())
        self.lock = Lock()
        self.usage = {}

    def reserve(self, route):
        now = self.clock()
        minute, day = int(now // 60), int(now // 86400)
        estimate = route.max_input_bytes + route.max_output_tokens + 1024

        with self.lock:
            usage = self.usage.setdefault(route.vendor, {})
            if usage.get("disabled"):
                raise CloudFailure("PROVIDER_CONFIGURATION_ERROR", "DISABLED", 0)
            if now < usage.get("until", 0):
                raise CloudFailure(
                    "PROVIDER_RATE_LIMITED",
                    "RATE_LIMITED",
                    int(usage["until"] - now) + 1,
                )
            if usage.get("minute") != minute:
                usage.update(minute=minute, requests_minute=0, tokens_minute=0)
            if usage.get("day") != day:
                usage.update(day=day, requests_day=0, tokens_day=0)
            if (
                usage["requests_minute"] >= route.rpm
                or usage["requests_day"] >= route.rpd
                or usage["tokens_minute"] + estimate > route.tpm
                or usage["tokens_day"] + estimate > route.tpd
            ):
                raise CloudFailure("PROVIDER_RATE_LIMITED", "RATE_LIMITED", 60)
            usage["requests_minute"] += 1
            usage["requests_day"] += 1
            usage["tokens_minute"] += estimate
            usage["tokens_day"] += estimate

    def feedback(self, route, failure):
        with self.lock:
            usage = self.usage.setdefault(route.vendor, {})
            if failure.state in {"DISABLED", "REVOKED"}:
                usage["disabled"] = True
            usage["until"] = self.clock() + failure.cooldown
