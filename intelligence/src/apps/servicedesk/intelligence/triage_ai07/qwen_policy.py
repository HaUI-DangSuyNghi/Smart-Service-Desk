"""Service Desk owns the frozen Kaggle prompt and letter interpretation."""

from hashlib import sha256
import json
from pathlib import Path

from hdpi.classification.protocols import require


ROOT = Path(__file__).parent
POLICY_PATH = ROOT / "qwen35-lora-policy-v0.1.json"
MAP_PATH = ROOT / "qwen35-letter-map-v0.1.json"
POLICY = json.loads(POLICY_PATH.read_bytes())
LETTER_MAP = json.loads(MAP_PATH.read_bytes())
POLICY_DIGEST = sha256(
    b"ai07-qwen-policy-v1\0"
    + POLICY_PATH.read_bytes()
    + b"\0"
    + MAP_PATH.read_bytes()
    + b"\0"
    + Path(__file__).read_bytes()
).hexdigest()
CLASSES = tuple(value for value in LETTER_MAP["letters"].values() if value is not None)


def messages(fields):
    require(type(fields) is dict and set(fields) == {"title", "description"})
    require(all(type(value) is str for value in fields.values()))
    require(0 < sum(map(len, fields.values())) <= 8192)
    ticket = json.dumps(fields, ensure_ascii=False, sort_keys=True)
    return [
        {"role": "system", "content": POLICY["system"]},
        {
            "role": "user",
            "content": POLICY["userTemplate"].replace("{ticket_json}", ticket),
        },
    ]


def adapter_configuration():
    """Inject app policy into the integration; no app import is needed there."""
    return {
        "policyDigest": POLICY_DIGEST,
        "allowedClasses": CLASSES,
        "abstainId": LETTER_MAP["abstainId"],
        "letters": dict(LETTER_MAP["letters"]),
        "messages": messages,
        "generation": dict(POLICY["generation"]),
    }
