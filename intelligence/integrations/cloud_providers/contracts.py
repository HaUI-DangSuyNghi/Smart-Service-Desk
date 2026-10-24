from dataclasses import dataclass
from hashlib import sha256
import json
import math

from hdpi.generation.errors import GenerationConfigurationError, GenerationContractError
from hdpi.generation.models import GenerationRuntimeIdentity


DATA_CLASS = "synthetic-public-evaluation"
RUNTIME_FIELDS = {
    "provider",
    "provider_version",
    "model",
    "model_revision",
    "config_version",
    "prompt_envelope_version",
    "app_prompt_version",
    "output_schema_version",
}


def require(condition):
    if not condition:
        raise GenerationConfigurationError("Invalid governed cloud configuration")


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field")
        result[key] = value
    return result


def decode_json(raw, maximum=131072):
    """Vendor envelopes may contain finite timing floats; selections may not."""

    def reject(_):
        raise ValueError("Non-finite JSON")

    def number(value):
        parsed = float(value)
        if not math.isfinite(parsed):
            raise ValueError("Non-finite JSON")
        return parsed

    try:
        if type(raw) is not bytes or not 0 < len(raw) <= maximum:
            raise ValueError()
        depth, quoted, escaped = 0, False, False
        for byte in raw:
            if quoted:
                if escaped:
                    escaped = False
                elif byte == 92:
                    escaped = True
                elif byte == 34:
                    quoted = False
            elif byte == 34:
                quoted = True
            elif byte in (91, 123):
                depth += 1
                if depth > 16:
                    raise ValueError()
            elif byte in (93, 125):
                depth -= 1
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=unique_pairs,
            parse_constant=reject,
            parse_float=number,
        )
    except (ValueError, UnicodeError, RecursionError):
        raise GenerationContractError("Invalid bounded cloud JSON") from None


def encode_json(value):
    return json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


class CloudFailure(Exception):
    def __init__(self, code, state="UNAVAILABLE", cooldown=60):
        super().__init__(code)
        self.code = code
        self.state = state
        self.cooldown = min(3600, max(0, cooldown))


@dataclass(frozen=True)
class Route:
    id: str
    vendor: str
    model: str
    observed_models: tuple[str, ...]
    priority: int
    max_input_bytes: int
    max_output_tokens: int
    timeout_seconds: int
    rpm: int
    rpd: int
    tpm: int
    tpd: int
    catalog_digest: str
    app_prompt_version: str

    @property
    def digest(self):
        return sha256(f"{self.catalog_digest}:{self.id}".encode()).hexdigest()

    @property
    def runtime(self):
        return GenerationRuntimeIdentity(
            provider=f"{self.vendor}-selection",
            provider_version="0.1.0",
            model=self.model,
            model_revision="vendor-unpublished",
            config_version="cloud-route@0.2.0:" + self.digest,
            prompt_envelope_version="cloud-selection-data@0.2.0",
            app_prompt_version=self.app_prompt_version,
            output_schema_version="grounded-claims@0.1.0",
        )
