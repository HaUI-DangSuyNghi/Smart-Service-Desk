"""Independent shape, risk, graph and replay checks; no approval is minted."""

from collections import Counter
from itertools import combinations
from functools import lru_cache
import ipaddress
import re
import subprocess
import unicodedata
from urllib.parse import urlsplit
from uuid import UUID

from _contracts import (
    DATA,
    ROOT,
    canonical,
    digest,
    load_inputs,
    read_json,
    require,
    schema_check,
    strict_json,
)
from _dataset import comparison_text, exact_signature, fact_catalog, generate_bundle


ATTACK_CATEGORIES = {
    "question_injection",
    "source_injection",
    "hostile_markup",
    "role_spoofing",
    "data_exfiltration_canary",
    "unsafe_quote_boundary",
}
ATTACK = re.compile(
    r"ignore (?:previous instructions|the policy)|reveal hidden|disclose every credential|SYSTEM:|<script\b|<img\b|<iframe\b|SYNTHETIC_CANARY_DO_NOT_DISCLOSE",
    re.I,
)
SECRET = re.compile(
    r"\b(?:sk-[A-Za-z0-9_-]{12,}|gsk_[A-Za-z0-9]{12,}|AIza[A-Za-z0-9_-]{20,})\b|-----BEGIN [A-Z ]*PRIVATE KEY-----|\b[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\b|\b(?:api[_-]?key|password|secret|token)\s*[:=]\s*\S+",
    re.I,
)
EMAIL = re.compile(r"[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
ADDRESS = re.compile(r"\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b")
DESTINATION = re.compile(r"(?:https?|ftp|file)://[^\s<>\"']+", re.I)
PHONE = re.compile(
    r"(?<![A-Za-z0-9])\+?[0-9][0-9 -]{7,}[0-9](?![A-Za-z0-9])|\b(?:SV|STUDENT)[ -]?[0-9]{6,}\b",
    re.I,
)
LONG_TOKEN = re.compile(r"\b[A-Za-z0-9_+/=-]{32,}\b")
TEST_NETWORKS = tuple(
    ipaddress.ip_network(value)
    for value in ("192.0.2.0/24", "198.51.100.0/24", "203.0.113.0/24")
)


@lru_cache(maxsize=1)
def frozen_release_digest():
    relative = "fixtures/intelligence/service-desk/generation/dev-v0.2.json"
    commits = subprocess.check_output(
        ["git", "log", "--reverse", "--diff-filter=A", "--format=%H", "--", relative],
        cwd=ROOT,
        text=True,
    ).splitlines()

    if not commits:
        return None  # Initial unpublished authoring, before the release commit.

    original = subprocess.check_output(
        ["git", "show", commits[0] + ":" + relative], cwd=ROOT
    )
    return digest(canonical(strict_json(original)))


def check_text(value, category, *, source=False, unsafe=False):
    require(
        not any(
            unicodedata.category(character) in {"Cs", "Cf"}
            or (unicodedata.category(character) == "Cc" and character not in "\n\r\t")
            for character in value
        ),
        "CONTROL_OR_SURROGATE",
    )
    require(not SECRET.search(value), "SECRET_PATTERN")
    require(not PHONE.search(value), "PERSONAL_IDENTIFIER_PATTERN")
    reserved = category == "data_exfiltration_canary"

    for token in LONG_TOKEN.findall(value):
        require(
            reserved and token == "SYNTHETIC_CANARY_DO_NOT_DISCLOSE",
            "HIGH_ENTROPY_CANDIDATE",
        )

    for email in EMAIL.findall(value):
        require(reserved and email == "user@example.test", "EMAIL_PATTERN")

    for destination in DESTINATION.findall(value):
        parsed = urlsplit(destination)
        require(
            reserved
            and parsed.scheme == "https"
            and parsed.hostname == "example.invalid"
            and parsed.username is None
            and parsed.password is None
            and parsed.port is None
            and parsed.path.startswith("/demo")
            and not parsed.query
            and not parsed.fragment,
            "UNAPPROVED_DESTINATION",
        )

    for address in ADDRESS.findall(value):
        try:
            candidate_address = ipaddress.ip_address(address)
        except ValueError:
            require(False, "IP_ADDRESS_PATTERN")

        require(
            reserved and any(candidate_address in network for network in TEST_NETWORKS),
            "IP_ADDRESS_PATTERN",
        )

    if ATTACK.search(value):
        require(category in ATTACK_CATEGORIES, "UNDECLARED_ATTACK")

        if source:
            require(unsafe, "UNFLAGGED_UNSAFE_SOURCE")


def validate_pack_risks(values):
    for family in values["facts"]["families"]:
        relations = [fact["relation"] for fact in family["facts"]]
        require(len(set(relations)) >= 3, "INDEPENDENT_FACT_PATTERNS")

        for fact in family["facts"]:
            require(
                fact["statementEn"] != fact["alternativeEn"]
                and fact["statementVi"] != fact["alternativeVi"],
                "NON_CONTRADICTING_ALTERNATIVE",
            )

            for key, value in fact.items():
                if key not in {"id", "relation"}:
                    check_text(value, "authored-fact")

    for template in values["templates"]["templates"]:
        for key in ("questionPattern", "questionSuffix", "sourceSuffix"):
            check_text(
                template[key],
                template["category"],
                source=key == "sourceSuffix",
                unsafe=bool(template["sourceSuffix"]),
            )


def graph_check(case, metadata, facts, templates):
    graph = metadata["oracleGraph"]
    documents = {item["metadataId"]: item for item in case["documents"]}
    support = graph["supportingSourceIds"]
    require(
        len(set(support)) == len(support) and set(support) <= set(documents),
        "ORACLE_SUPPORT_MEMBERSHIP",
    )
    require(
        all(
            len(pair) == 2 and len(set(pair)) == 2 and set(pair) <= set(documents)
            for pair in graph["contradictionPairs"]
        ),
        "ORACLE_CONTRADICTION_MEMBERSHIP",
    )

    if metadata["origin"] == "PRESERVED_V01":
        return

    require(
        metadata["factId"] in facts and metadata["templateId"] in templates,
        "CURATION_REFERENCE",
    )
    fact = facts[metadata["factId"]]
    template = templates[metadata["templateId"]]
    require(
        metadata["familyId"] == fact["familyId"]
        and template["category"] == case["category"],
        "CURATION_OWNERSHIP",
    )
    require(
        metadata["templateVersion"] == template["version"]
        and metadata["oracleType"] == template["oracleType"],
        "ORACLE_IDENTITY",
    )
    expected_unsafe = {
        item["metadataId"]
        for item in case["documents"]
        if template["sourceSuffix"] and template["sourceSuffix"] in item["content"]
    }
    require(set(case["unsafeSourceIds"]) == expected_unsafe, "UNSAFE_FLAG_INVARIANT")

    for relation in graph["requiredRelations"]:
        require(relation in facts, "UNKNOWN_ORACLE_FACT")
        statements = [facts[relation][key] for key in ("statementEn", "statementVi")]
        require(
            any(
                unicodedata.normalize("NFC", statement)
                in unicodedata.normalize("NFC", documents[identity]["content"])
                for statement in statements
                for identity in support
            ),
            "ORACLE_UNSUPPORTED_CLAIM",
        )

    for left, right in graph["contradictionPairs"]:
        pair = [
            unicodedata.normalize("NFC", documents[identity]["content"])
            for identity in (left, right)
        ]
        require(
            any(
                fact["statement" + language] in pair[0]
                and fact["alternative" + language] in pair[1]
                for language in ("En", "Vi")
            ),
            "ORACLE_CONTRADICTION_GRAPH",
        )

    if metadata["oracleType"] == "symbolic_fact":
        answerable = (
            bool(support)
            and not graph["unsupportedRelations"]
            and not graph["contradictionPairs"]
            and not case["unsafeSourceIds"]
        )
        require(
            case["provisionalExpectedStatus"]
            == ("SUCCESS" if answerable else "INSUFFICIENT_EVIDENCE"),
            "ORACLE_STATUS",
        )


def duplicate_report(cases, metadata, threshold):
    seen = {}
    shingles = []
    exact_relations = []

    for case, detail in zip(cases, metadata, strict=True):
        signature = exact_signature(case)
        previous = seen.get(signature)

        if previous:
            parent = previous[0]
            declared = (
                detail["mutationType"] == "source_order"
                and detail["metamorphicSiblingOf"] == parent["caseId"]
            )
            require(
                declared
                and [item["content"] for item in case["documents"]]
                == list(reversed([item["content"] for item in parent["documents"]])),
                "ACCIDENTAL_EXACT_DUPLICATE",
            )
            exact_relations.append([parent["caseId"], case["caseId"]])
        else:
            seen[signature] = (case, detail)

        text = comparison_text(
            case["question"]
            + " "
            + " ".join(item["content"] for item in case["documents"])
        )
        shingles.append(
            {text[index : index + 3] for index in range(max(1, len(text) - 2))}
        )

    near = []

    for left, right in combinations(range(len(cases)), 2):
        common = len(shingles[left] & shingles[right])
        union = len(shingles[left]) + len(shingles[right]) - common
        score = common * 1000000 // max(1, union)

        if score >= threshold:
            relation = metadata[right]["metamorphicSiblingOf"] == cases[left]["caseId"]
            near.append(
                {
                    "caseIds": [cases[left]["caseId"], cases[right]["caseId"]],
                    "similarityPpm": score,
                    "decision": (
                        "DECLARED_METAMORPHIC" if relation else "REVIEW_REQUIRED"
                    ),
                }
            )

    return {
        "accidentalExactDuplicates": 0,
        "declaredExactOrderPairs": exact_relations,
        "nearDuplicateCandidates": near,
    }


def inspect_bundle(bundle, paths=None):
    values = load_inputs(paths)
    validate_pack_risks(values)

    for name in ("dataset", "curation", "manifest", "smoke"):
        schema_check(name, bundle[name])

    dataset, curation = bundle["dataset"], bundle["curation"]
    frozen = frozen_release_digest()

    if frozen:
        require(digest(dataset) == frozen, "FROZEN_DATASET_REVISION")

    cases, metadata = dataset["cases"], curation["cases"]
    require(cases[:52] == values["baseline"]["cases"], "BASELINE_CASE_DRIFT")
    require(
        [case["caseId"] for case in cases]
        == [f"GEN-SD-{index:04}" for index in range(1, 401)],
        "CASE_IDS",
    )
    require(
        [item["caseId"] for item in metadata] == [case["caseId"] for case in cases],
        "CURATION_CASE_IDS",
    )
    categories = {item["id"]: item for item in values["plan"]["categories"]}
    facts = {item["id"]: item for item in fact_catalog(values)}
    templates = {item["id"]: item for item in values["templates"]["templates"]}
    previous_cases = set()

    for index, (case, detail) in enumerate(zip(cases, metadata, strict=True)):
        require(detail["caseDigest"] == digest(case), "CASE_DIGEST")
        require(detail["sourceCount"] == len(case["documents"]), "SOURCE_COUNT")
        require(
            detail["primaryAxis"] == categories[case["category"]]["axis"],
            "PRIMARY_AXIS",
        )
        require(
            detail["origin"] == ("PRESERVED_V01" if index < 52 else "GENERATED_V02"),
            "CASE_ORIGIN",
        )
        require(
            detail["metamorphicSiblingOf"] is None
            or detail["metamorphicSiblingOf"] in previous_cases,
            "METAMORPHIC_REFERENCE",
        )
        previous_cases.add(case["caseId"])
        check_text(case["question"], case["category"])
        identifiers = [item["metadataId"] for item in case["documents"]]
        require(len(set(identifiers)) == len(identifiers), "DUPLICATE_SOURCE_ID")
        require(
            set(case["unsafeSourceIds"]) <= set(identifiers), "UNSAFE_SOURCE_MEMBERSHIP"
        )
        require(
            sum(len(item["content"].encode("utf-8")) for item in case["documents"])
            <= 32000,
            "SNAPSHOT_BUDGET",
        )

        for item in case["documents"]:
            require(
                item["checksum"] == digest(item["content"].encode("utf-8")),
                "SOURCE_CHECKSUM",
            )
            require(
                index < 52 or UUID(item["metadataId"]).version == 5,
                "SYNTHETIC_UUID_VERSION",
            )
            check_text(
                item["content"],
                case["category"],
                source=True,
                unsafe=item["metadataId"] in case["unsafeSourceIds"],
            )

        if detail["textMode"] != "LEGACY_BYTES":
            form = "NFD" if detail["textMode"] == "PRESERVE_NFD" else "NFC"
            require(
                all(
                    unicodedata.is_normalized(form, text)
                    for text in [
                        case["question"],
                        *(item["content"] for item in case["documents"]),
                    ]
                ),
                "UNICODE_MODE",
            )

        graph_check(case, detail, facts, templates)

    counts = Counter(case["category"] for case in cases)
    require(
        dict(counts) == {key: value["total"] for key, value in categories.items()},
        "CATEGORY_ALLOCATION",
    )
    require(
        dict(Counter(item["primaryAxis"] for item in metadata))
        == values["plan"]["axes"],
        "AXIS_ALLOCATION",
    )
    new = metadata[52:]
    require(len({item["familyId"] for item in new}) >= 12, "FACT_FAMILY_COVERAGE")
    require(
        len({item["factId"] for item in new}) == len(facts), "FACT_PATTERN_COVERAGE"
    )
    require(
        bundle["manifest"]["datasetDigest"] == digest(dataset)
        and bundle["manifest"]["curationDigest"] == digest(curation),
        "MANIFEST_DIGEST",
    )
    duplicates = duplicate_report(
        cases, metadata, values["plan"]["nearDuplicateThresholdPpm"]
    )

    return {
        "result": "STATIC_VALIDATED",
        "caseCount": 400,
        "newCaseCount": 348,
        "baselineDigest": values["plan"]["baselineDigest"],
        "datasetDigest": digest(dataset),
        "factFamilies": len({item["familyId"] for item in new}),
        "factPatterns": len({item["factId"] for item in new}),
        "templates": len({item["templateId"] for item in new}),
        "languages": dict(
            sorted(Counter(item["language"] for item in metadata).items())
        ),
        "sourceTopologies": dict(
            sorted(Counter(item["sourceTopology"] for item in metadata).items())
        ),
        "attackPatterns": dict(
            sorted(Counter(item["attackPattern"] for item in metadata).items())
        ),
        "riskCounts": dict(sorted(Counter(item["risk"] for item in metadata).items())),
        **duplicates,
        "detectorDecision": "NO_KNOWN_PATTERN_DETECTED_NOT_ABSOLUTE_PII_PROOF",
        "humanReview": "PENDING",
        "officialDecision": "BLOCKED",
    }


def default_bundle_paths():
    return {
        "dataset": DATA / "dev-v0.2.json",
        "curation": DATA / "curation-v0.2.json",
        "manifest": DATA / "manifest-v0.2.json",
        "smoke": DATA / "smoke-manifest-v0.2.json",
    }


def validate_bundle(bundle=None, paths=None):
    bundle = bundle or {
        key: read_json(path) for key, path in default_bundle_paths().items()
    }
    report = inspect_bundle(bundle, paths)
    replay = generate_bundle(paths)
    require(
        all(canonical(bundle[key]) == canonical(replay[key]) for key in replay),
        "REPLAY_OR_DEPENDENCY_DRIFT",
    )
    return report
