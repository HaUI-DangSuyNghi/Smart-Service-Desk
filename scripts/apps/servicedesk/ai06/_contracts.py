"""Application-owned immutable inputs and strict JSON contracts for DEV data."""

from hashlib import sha256
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[4]
DATA = ROOT / "fixtures/intelligence/service-desk/generation"
TOOLS = Path(__file__).resolve().parent
BASELINE_REVISION = "1940b6eca68eca2509bef43280065412e33de42a"
BASELINE_DIGEST = "f3ba65dd318b2e06cce0e450faf7f2978916e8a6001c94270de38cb23fcb0c2e"
SEED = 20261024


class DatasetError(ValueError):
    """Only fixed codes, never source content or raw parser diagnostics."""


def require(condition, code):
    if not condition:
        raise DatasetError(code)


def canonical(value):
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def digest(value):
    raw = value if type(value) is bytes else canonical(value)
    return sha256(raw).hexdigest()


def strict_json(raw):
    def pairs(items):
        result = {}

        for key, value in items:
            require(key not in result, "DUPLICATE_JSON_KEY")
            result[key] = value

        return result

    def reject(_):
        raise DatasetError("NON_FINITE_JSON")

    try:
        require(type(raw) is bytes and 0 < len(raw) <= 16 * 1024 * 1024, "JSON_SIZE")
        return json.loads(
            raw.decode("utf-8"), object_pairs_hook=pairs, parse_constant=reject
        )
    except (UnicodeError, RecursionError, ValueError) as error:
        if isinstance(error, DatasetError):
            raise

        raise DatasetError("INVALID_JSON") from None


def read_json(path):
    try:
        path = Path(path)
        require(
            path.is_file() and 0 < path.stat().st_size <= 16 * 1024 * 1024, "INPUT_SIZE"
        )
        return strict_json(path.read_bytes())
    except OSError:
        raise DatasetError("INPUT_UNAVAILABLE") from None


def schema_check(name, value):
    schema = read_json(DATA / "schemas" / (name + ".schema.json"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    require(not any(validator.iter_errors(value)), "SCHEMA_" + name.upper())


def default_inputs():
    return {
        "baseline": DATA / "dev-v0.1.json",
        "plan": DATA / "generation-plan-v0.2.json",
        "facts": DATA / "source-facts-v0.2.json",
        "templates": DATA / "templates-v0.2.json",
    }


def load_inputs(paths=None, seed=SEED):
    paths = paths or default_inputs()
    values = {
        key: read_json(paths[key]) for key in ("baseline", "plan", "facts", "templates")
    }
    plan = values["plan"]
    baseline = values["baseline"]

    for name in ("plan", "facts", "templates"):
        schema_check(name, values[name])

    require(
        digest(Path(paths["baseline"]).read_bytes())
        == BASELINE_DIGEST
        == plan["baselineDigest"],
        "BASELINE_DRIFT",
    )
    require(plan["baselineRevision"] == BASELINE_REVISION, "BASELINE_REVISION")
    require(type(seed) is int and seed == plan["seed"] == SEED, "SEED_MISMATCH")
    require(len(baseline["cases"]) == 52, "BASELINE_COUNT")
    require(
        [case["caseId"] for case in baseline["cases"]]
        == [f"GEN-SD-{index:04}" for index in range(1, 53)],
        "BASELINE_IDS",
    )

    for name in ("facts", "templates"):
        require(
            digest(Path(paths[name]).read_bytes()) == plan[name + "Digest"],
            "INPUT_PIN_" + name.upper(),
        )

    categories = plan["categories"]
    require(len({item["id"] for item in categories}) == 30, "CATEGORY_UNIQUENESS")
    require(
        sum(item["baselineCount"] for item in categories) == 52, "ALLOCATION_BASELINE"
    )
    require(sum(item["newCount"] for item in categories) == 348, "ALLOCATION_NEW")
    require(
        all(
            item["baselineCount"] + item["newCount"] == item["total"]
            for item in categories
        ),
        "ALLOCATION_TOTAL",
    )
    require(1 <= plan["maxAttemptsPerCase"] <= 20, "ATTEMPT_BOUND")
    require(plan["nearDuplicateThresholdPpm"] == 900000, "NEAR_DUPLICATE_POLICY")

    for axis, total in plan["axes"].items():
        require(
            sum(item["total"] for item in categories if item["axis"] == axis) == total,
            "AXIS_ALLOCATION",
        )

    families = values["facts"]["families"]
    fact_ids = [fact["id"] for family in families for fact in family["facts"]]
    require(
        len({family["id"] for family in families}) == len(families), "DUPLICATE_FAMILY"
    )
    require(len(set(fact_ids)) == len(fact_ids), "DUPLICATE_FACT")

    templates = values["templates"]["templates"]
    require(
        len({item["id"] for item in templates}) == len(templates), "DUPLICATE_TEMPLATE"
    )

    for category in categories:
        selected = [item for item in templates if item["category"] == category["id"]]
        require(len(selected) >= 2, "TEMPLATE_COVERAGE")
        require(
            all(item["questionPattern"].count("{question}") == 1 for item in selected),
            "TEMPLATE_PLACEHOLDER",
        )

    return values


def tooling_pins():
    return {path.name: digest(path.read_bytes()) for path in sorted(TOOLS.glob("*.py"))}


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical(value))
