"""Validate N1 authoring artifacts locally; no AI engine, HTTP, DB or ingestion."""

import copy
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import json
import math
from pathlib import Path
import re
import sys

try:
    import yaml
    from jsonschema import Draft202012Validator
    from jsonschema.exceptions import ValidationError
except ImportError:
    raise SystemExit(
        "Missing local validation tool: PyYAML/jsonschema. No package was installed. "
        "Use an authoring environment with these tools; they are not AI runtime dependencies."
    )

REPO_ROOT = Path(__file__).resolve().parents[2]
CONTRACT_DIR = REPO_ROOT / "contracts/intelligence/v1"
PROFILE_DIR = REPO_ROOT / "intelligence/profiles/service-desk"
FIXTURE_DIR = REPO_ROOT / "fixtures/intelligence/service-desk"
KNOWLEDGE_DIR = FIXTURE_DIR / "knowledge/sources"
SCHEMA_FILENAMES = (
    "triage-request.schema.json",
    "triage-result.schema.json",
    "search-request.schema.json",
    "search-result.schema.json",
)
DRAFT_URI = "https://json-schema.org/draft/2020-12/schema"


def ensure(condition, message):
    if not condition:
        raise ValueError(message)


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        ensure(key not in result, f"Duplicate key: {key}")
        result[key] = value
    return result


class UniqueYamlLoader(yaml.SafeLoader):
    pass


def unique_yaml_mapping(loader, node):
    return unique_pairs(
        (loader.construct_object(key), loader.construct_object(value))
        for key, value in node.value
    )


UniqueYamlLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_yaml_mapping
)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs, parse_constant=reject_json_constant)


def reject_json_constant(value):
    raise ValueError(f"Non-JSON numeric constant: {value}")


def local_artifact(relative):
    path = (REPO_ROOT / relative).resolve()
    ensure(path.is_relative_to(FIXTURE_DIR), f"Artifact escapes Service Desk fixtures/: {relative}")
    ensure(path.is_file(), f"Missing artifact: {relative}")
    return path


def validate_digest(record, path, checksum_key):
    raw = path.read_bytes()
    ensure(b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"), f"Expected LF/UTF-8 without BOM: {path.name}")
    raw.decode("utf-8")
    ensure(raw.endswith(b"\n"), f"Missing final newline: {path.name}")
    ensure(re.fullmatch(r"[a-f0-9]{64}", record[checksum_key]) is not None, "Invalid SHA-256 format")
    ensure(hashlib.sha256(raw).hexdigest() == record[checksum_key], f"Stale exact-byte digest: {path.name}")
    ensure(record["byteSize"] == len(raw), f"Wrong byte size: {path.name}")


def read_yaml(path):
    return yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueYamlLoader)


def validate_shared_schema(schema, domain_literals):
    ensure(schema.get("$schema") == DRAFT_URI, "Shared schemas must declare Draft 2020-12")
    Draft202012Validator.check_schema(schema)

    def reject_domain_literal(value):
        if isinstance(value, str):
            ensure(value not in domain_literals, f"Domain-specific literal in shared schema: {value}")
        elif isinstance(value, list):
            for item in value:
                reject_domain_literal(item)
        elif isinstance(value, dict):
            for item in value.values():
                reject_domain_literal(item)

    def visit(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key in {"enum", "const", "default"}:
                    reject_domain_literal(value)
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(schema)


def validate_profile(profile, taxonomy, rules, policy):
    ensure(set(profile) == {"status", "profile", "taxonomy", "rules", "priorityPolicy", "normalization"}, "Unexpected profile fields")
    ensure(all(document["status"] == "PROPOSED" for document in (profile, taxonomy, rules, policy)), "Profile artifacts must remain proposed")
    ensure(profile["profile"] == {"id": "service-desk", "version": "service-desk@0.1.0"}, "Profile identity/version drift")
    pins = {
        "taxonomy": {"version": taxonomy["taxonomyVersion"], "file": "taxonomy.yaml"},
        "rules": {"version": rules["rulesVersion"], "file": "rules.yaml"},
        "priorityPolicy": {"version": policy["policyVersion"], "file": "priority-policy.yaml", "owner": policy["owner"]},
        "normalization": {"version": "text-normalization@0.1.0", "file": "normalization.md"},
    }
    for section, pin in pins.items():
        ensure(profile[section] == pin, f"Profile file/version pin drift: {section}")
        ensure((PROFILE_DIR / pin["file"]).is_file(), f"Missing profile file: {section}")
    ensure(taxonomy["taxonomyVersion"] == rules["rulesVersion"] == "0.1.0", "Taxonomy/rules version drift")
    ensure(policy["policyVersion"] == "priority-policy@0.1.0", "Priority policy version drift")
    ensure(policy["owner"] == "Service Desk / Process Owner", "Priority business ownership drift")
    ensure(policy["technicalImplementer"] == "Intelligence" and policy["requiredReviewers"] == ["BE", "Process/Service Owner"], "Priority review ownership drift")
    normalization = (PROFILE_DIR / "normalization.md").read_text(encoding="utf-8")
    ensure(f"Proposed version: `{profile['normalization']['version']}`." in normalization, "Normalization version drift")
    ensure(set(taxonomy) == {"status", "taxonomyVersion", "categories"}, "Mixed concern in taxonomy")
    ensure(set(rules) == {"status", "rulesVersion", "categoryRules", "conflictRules", "reviewRules", "impactRules", "urgencyRules", "priorityRuleIds"}, "Mixed concern in rules")
    categories = taxonomy["categories"]
    ensure(set(categories) == {"NETWORK", "ACCOUNT", "SOFTWARE", "HARDWARE", "OTHER"}, "Expected five categories")
    for name, category in categories.items():
        ensure(set(category) == {"definition", "exclusions", "suggestedGroup"}, f"Mixed category concern: {name}")
        ensure(isinstance(category["definition"], str) and category["definition"].strip(), f"Missing definition: {name}")
        ensure(isinstance(category["suggestedGroup"], str) and category["suggestedGroup"].strip(), f"Missing routing: {name}")
        exclusions = category["exclusions"]
        ensure(isinstance(exclusions, list) and exclusions and all(isinstance(item, str) and item.strip() for item in exclusions), f"Missing exclusions: {name}")
    matched_categories = []
    for rule_id, rule in rules["categoryRules"].items():
        ensure(set(rule) == {"category", "positiveSignals"}, f"Unexpected category rule fields: {rule_id}")
        ensure(rule["category"] in categories, f"Unknown rule category: {rule_id}")
        signals = rule["positiveSignals"]
        ensure(isinstance(signals, list) and signals and all(isinstance(item, str) and item.strip() for item in signals), f"Missing positive signals: {rule_id}")
        matched_categories.append(rule["category"])
    ensure(len(matched_categories) == len(categories) and set(matched_categories) == set(categories), "Category rule coverage/uniqueness drift")
    ensure(rules["conflictRules"] == {"CONFLICT-ROOT-001": {"principle": "Root cause có evidence rõ ưu tiên hơn symptom; không dùng thứ tự category để đoán."}}, "Conflict rule drift")
    ensure(rules["reviewRules"] == {"REVIEW-INSUFFICIENT-001": {"policyRef": "priority-policy.yaml#/reviewFallback"}}, "Review rule must reference policy fallback")
    ensure(policy["reviewFallback"] == {
        "category": "OTHER", "priority": "MEDIUM", "suggestedGroup": "GENERAL-SUPPORT",
        "impact": None, "urgency": None, "needsReview": True, "reasonCode": "INSUFFICIENT_INFORMATION",
    }, "Ambiguous fallback drift")
    ensure(policy["reviewFallback"]["needsReview"] is True, "Review fallback must require review")
    ensure(set(rules["impactRules"]) == set(policy["impact"]) == set(policy["matrix"]) == {"I1", "I2", "I3", "I4"}, "Incomplete impact matrix")
    ensure(set(rules["urgencyRules"]) == set(policy["urgency"]) == {"U1", "U2", "U3", "U4"}, "Incomplete urgency rules")
    ensure(set(rules["priorityRuleIds"]) == {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, "Incomplete priorities")
    for mapping in (policy["impact"], policy["urgency"]):
        ensure(all(isinstance(value, str) and value.strip() for value in mapping.values()), "Missing impact/urgency definitions")
    expected_rows = {
        "I1": ["LOW", "LOW", "MEDIUM", "HIGH"],
        "I2": ["LOW", "MEDIUM", "HIGH", "HIGH"],
        "I3": ["MEDIUM", "HIGH", "HIGH", "CRITICAL"],
        "I4": ["HIGH", "HIGH", "CRITICAL", "CRITICAL"],
    }
    expected_matrix = {impact: dict(zip(["U1", "U2", "U3", "U4"], priorities)) for impact, priorities in expected_rows.items()}
    ensure(policy["matrix"] == expected_matrix, "Priority matrix differs from supplied N1 policy")
    # Historical knowledge is an immutable fixture, not another policy authority.
    source = KNOWLEDGE_DIR / "priority-policy.md"
    rows = re.findall(r"^\| (I[1-4]) \| (LOW|MEDIUM|HIGH|CRITICAL) \| (LOW|MEDIUM|HIGH|CRITICAL) \| (LOW|MEDIUM|HIGH|CRITICAL) \| (LOW|MEDIUM|HIGH|CRITICAL) \|$", source.read_text(encoding="utf-8"), re.MULTILINE)
    ensure({row[0]: list(row[1:]) for row in rows} == expected_rows, "Knowledge fixture matrix drift")
    rule_ids = list(rules["categoryRules"]) + list(rules["conflictRules"]) + list(rules["reviewRules"])
    for field in ("impactRules", "urgencyRules", "priorityRuleIds"):
        rule_ids.extend(rules[field].values())
    ensure(all(isinstance(rule_id, str) and rule_id.strip() for rule_id in rule_ids), "Blank rule IDs")
    ensure(len(set(rule_ids)) == len(rule_ids), "Duplicate rule IDs")


def validate_fixture(fixture, profile, taxonomy, rules, policy, request_validator, response_validator):
    ensure(set(fixture) == {"id", "datasetVersion", "annotationKind", "scenario", "request", "expected"}, "Unexpected fixture fields")
    ensure(fixture["datasetVersion"] == "0.1.0" and fixture["annotationKind"] == "MANUAL_EXPECTED", "Expected manual annotation")
    request, response = fixture["request"], fixture["expected"]
    request_validator.validate(request)
    response_validator.validate(response)
    ensure(response["status"] == "SUCCESS", "Ticket fixture must contain expected suggestion")
    validate_echo(request, response)
    ensure(request["profile"] == profile["profile"]["id"] and request["context"]["workspaceId"] == "lab-a", "Fixture context drift")
    result, explanation = response["result"], response["explanation"]
    category = taxonomy["categories"][result["category"]]
    category_rule_id = next(rule_id for rule_id, rule in rules["categoryRules"].items() if rule["category"] == result["category"])
    ensure(result["suggestedGroup"] == category["suggestedGroup"], "Routing mismatch")
    for evidence in response["evidence"]:
        ensure(evidence["type"] == "INPUT", "Triage must not contain knowledge evidence")
        ensure(evidence["text"] in request["input"][evidence["field"]], "Fabricated input evidence")
    ensure(response["runtime"] == {"engine": "rules", "profileVersion": profile["profile"]["version"], "rulesVersion": rules["rulesVersion"]}, "Runtime annotation version drift")
    required_rules = {category_rule_id}
    if fixture["scenario"] == "ambiguous":
        fallback = policy["reviewFallback"]
        ensure(result == {field: fallback[field] for field in ("category", "priority", "suggestedGroup")}, "Wrong review fallback")
        ensure(response["needsReview"] is True and explanation["reasonCode"] == fallback["reasonCode"], "Missing insufficient-information review")
        ensure(explanation["impact"] is None and explanation["urgency"] is None, "Ambiguous fixture invents impact/urgency")
        required_rules.add(next(iter(rules["reviewRules"])))
    else:
        ensure(fixture["scenario"] in {"clear", "root-cause-conflict"}, "Unknown fixture scenario")
        ensure(response["needsReview"] is False, "Clear scenario has unexpected review")
        impact, urgency = explanation["impact"], explanation["urgency"]
        ensure(result["priority"] == policy["matrix"][impact][urgency], "Priority differs from impact x urgency")
        required_rules.update([rules["impactRules"][impact], rules["urgencyRules"][urgency], rules["priorityRuleIds"][result["priority"]]])
        conflict = fixture["scenario"] == "root-cause-conflict"
        ensure(explanation["reasonCode"] == ("ROOT_CAUSE_CONFLICT" if conflict else "CLASSIFIED"), "Reason/scenario mismatch")
        if conflict:
            required_rules.add(next(iter(rules["conflictRules"])))
    ensure(set(explanation["matchedRules"]) == required_rules, "Missing/unknown/misapplied rule IDs")


def validate_knowledge(manifest):
    ensure(manifest["manifestVersion"] == "0.1.0" and manifest["status"] == "DRAFT_NOT_INGESTED", "Knowledge state drift")
    artifacts = manifest["artifacts"]
    ensure(len(artifacts) == 5 and len({item["artifactId"] for item in artifacts}) == 5, "Expected five distinct knowledge documents")
    ensure({item["path"] for item in artifacts} == {path.relative_to(REPO_ROOT).as_posix() for path in KNOWLEDGE_DIR.glob("*.md")}, "Knowledge manifest/file mismatch")
    for item in artifacts:
        path = local_artifact(item["path"])
        validate_digest(item, path, "checksum")
        ensure(item["checksumAlgorithm"] == "SHA-256" and item["checksumTrust"] == "LOCAL_EXACT_BYTES", "Cannot claim Core-verified checksum")
        ensure(item["rightsStatus"] == "PENDING_OWNER_APPROVAL" and item["usageRights"] is None, "Unapproved knowledge license")
        ensure(item["metadataId"] is None and item["workspaceId"] is None and item["ingestionStatus"] == "NOT_INGESTED", "Unexpected/fabricated Core binding")
        text = path.read_text(encoding="utf-8")
        ensure(text.startswith(f"# {item['title']}\n"), "Knowledge title drift")
        for line in (f"Artifact ID: `{item['artifactId']}`", f"Version: `{item['version']}`", f"Source: `{item['source']}`", "Rights status: `PENDING_OWNER_APPROVAL`"):
            ensure(line in text, f"Knowledge provenance header mismatch: {path.name}")
        ensure(item["version"] == "0.1.0" and item["source"] == f"team-authored:knowledge/{path.stem}", "Knowledge source/version drift")


def validate_echo(request, response):
    for field in ("contractVersion", "requestId", "profile"):
        ensure(request[field] == response[field], f"Context echo mismatch: {field}")
    ensure(request["context"]["workspaceId"] == response["workspaceId"], "Trusted workspace echo mismatch")


def validate_search(pair, request_validator, response_validator):
    request, response = pair["request"], pair["expected"]
    request_validator.validate(request)
    response_validator.validate(response)
    validate_echo(request, response)
    ensure(len(response["results"]) <= request["topK"], "Search hit count exceeds requested topK")
    for hit in response["results"]:
        ensure(hit["workspaceId"] == request["context"]["workspaceId"], "Search hit workspace mismatch")
        ensure(math.isfinite(hit["score"]), "Non-finite ranking score")


def validate_retrieval_gold(gold, fixtures, knowledge):
    ensure(gold["datasetVersion"] == "0.1.0" and gold["status"] == "DRAFT", "Gold version/state drift")
    ensure(gold["annotationKind"] == "MANUAL_EXPECTED" and gold["benchmarkStatus"] == "NOT BENCHMARK"
           and gold["outputKind"] == "NOT ENGINE OUTPUT", "Gold must remain manual authoring data")
    ensure(gold["rightsStatus"] == "PENDING_OWNER_APPROVAL" and gold["usageRights"] is None, "Unapproved retrieval-gold rights")
    ensure([case["ticketId"] for case in gold["cases"]] == [fixture["id"] for fixture in fixtures], "Gold ticket coverage/order drift")
    known = {item["artifactId"] for item in knowledge["artifacts"]}
    for case in gold["cases"]:
        ensure(set(case) == {"ticketId", "expectedRelevantLocalArtifacts"}, "Gold is not a runtime citation envelope")
        ids = case["expectedRelevantLocalArtifacts"]
        ensure(ids and len(ids) == len(set(ids)) and set(ids) <= known, "Unknown/duplicate/empty gold artifact IDs")


def reject_mutation(label, value, validator):
    try:
        validator(value)
    except (ValueError, KeyError, ValidationError):
        print(f"PASS reject {label}")
        return
    raise AssertionError(f"Invalid mutation accepted: {label}")


def main():
    # The historical v0.1 validator remains intact; new versions are checked too.
    import runpy
    executable = runpy.run_path(str(REPO_ROOT / 'scripts/intelligence/validate-executable-profiles.py'))
    executable['main']()
    print("Validation time (UTC):", datetime.now(timezone.utc).isoformat(timespec="seconds"))
    profile, taxonomy, rules, policy = [read_yaml(PROFILE_DIR / filename) for filename in
        ("profile.yaml", "taxonomy.yaml", "rules.yaml", "priority-policy.yaml")]
    validate_profile(profile, taxonomy, rules, policy)
    ensure((CONTRACT_DIR / "contract.md").is_file(), "Missing shared capability contract")
    ensure(
        {path.name for path in CONTRACT_DIR.glob("*.schema.json")} == set(SCHEMA_FILENAMES),
        "Expected exactly four shared capability schemas",
    )
    domain_literals = (
        set(taxonomy["categories"])
        | {category["suggestedGroup"] for category in taxonomy["categories"].values()}
        | set(rules["priorityRuleIds"])
        | {profile["profile"]["id"]}
    )
    validators = []
    schemas = {}
    for filename in SCHEMA_FILENAMES:
        schema = read_json(CONTRACT_DIR / filename)
        validate_shared_schema(schema, domain_literals)
        schemas[filename] = schema
        validators.append(Draft202012Validator(schema))
        if filename.endswith("request.schema.json"):
            ensure("SERVER-INJECTED / TRUSTED ADAPTER CONTEXT" in schema["properties"]["context"]["description"], "Trusted context description missing")
    request_validator, response_validator, search_request_validator, search_response_validator = validators
    path = FIXTURE_DIR / "tickets/tickets-v0.1.jsonl"
    fixtures = [json.loads(line, object_pairs_hook=unique_pairs, parse_constant=reject_json_constant) for line in path.read_text(encoding="utf-8").splitlines()]
    ensure([item["id"] for item in fixtures] == [f"TICKET-{n:03}" for n in range(1, 13)], "Expected exactly TICKET-001..012")
    ensure(len({item["request"]["requestId"] for item in fixtures}) == 12, "Duplicate request IDs")
    for fixture in fixtures:
        validate_fixture(fixture, profile, taxonomy, rules, policy, request_validator, response_validator)
    ensure({item["expected"]["result"]["category"] for item in fixtures} == set(taxonomy["categories"]), "Category coverage incomplete")
    ensure({item["expected"]["result"]["priority"] for item in fixtures} == set(rules["priorityRuleIds"]), "Priority coverage incomplete")
    manifest = read_json(FIXTURE_DIR / "tickets/manifest.json")
    ensure(local_artifact(manifest["artifactPath"]) == path, "Wrong dataset path")
    validate_digest(manifest, path, "sha256")
    ensure(manifest["recordCount"] == len(fixtures) and manifest["datasetVersion"] == "0.1.0", "Dataset manifest count/version mismatch")
    ensure(manifest["dataKind"] == "SYNTHETIC" and manifest["annotationKind"] == "MANUAL_EXPECTED", "Dataset must be synthetic expected annotations")
    ensure(manifest["rightsStatus"] == "PENDING_OWNER_APPROVAL" and manifest["usageRights"] is None, "Unapproved dataset rights")
    knowledge = read_json(FIXTURE_DIR / "knowledge/manifest.json")
    validate_knowledge(knowledge)
    degraded = read_json(FIXTURE_DIR / "tickets/triage-degraded-v0.1.json")
    response_validator.validate(degraded)
    ensure(degraded["status"] == "DEGRADED", "Expected degraded example")
    validate_echo(fixtures[0]["request"], {**degraded, "requestId": fixtures[0]["request"]["requestId"]})
    gold = read_json(FIXTURE_DIR / "retrieval/gold-v0.1.json")
    validate_retrieval_gold(gold, fixtures, knowledge)
    # Shape-only probe using the real synthetic Core record documented in the
    # 2026-10-07 acceptance run. No UUID is invented or assigned to local knowledge.
    # Ranking/chunk/index below are illustrative, not retrieval engine output.
    search = {
        "request": {"contractVersion": "0.1.0", "requestId": "schema-search-001", "profile": "service-desk",
                    "context": {"workspaceId": "lab-a"}, "query": "synthetic provenance probe", "topK": 3},
        "expected": {"contractVersion": "0.1.0", "requestId": "schema-search-001", "profile": "service-desk",
                     "workspaceId": "lab-a", "status": "SUCCESS", "results": [{
                         "workspaceId": "lab-a", "metadataId": "529e4be2-53f5-4b45-84f4-aed6c2d82c0c",
                         "sourceVersion": "rev-1", "sourceChecksum": hashlib.sha256(b"AI-N1 synthetic provenance probe.").hexdigest(),
                         "chunkId": "illustrative-chunk-001", "score": 0.72, "excerpt": "AI-N1 synthetic provenance probe."}],
                     "runtime": {"engine": "schema-probe", "engineVersion": "illustrative@0.1.0", "profileVersion": "service-desk@0.1.0",
                                 "corpusVersion": "illustrative@0.1.0", "indexVersion": "illustrative-index-001"},
                     "needsReview": False, "error": None},
    }

    def search_check(value):
        validate_search(value, search_request_validator, search_response_validator)

    search_check(search)
    empty = copy.deepcopy(search)
    empty["expected"]["results"] = []
    search_check(empty)
    search_degraded = copy.deepcopy(empty)
    search_degraded["expected"].update(status="DEGRADED", runtime=None, needsReview=True, error={"code": "INTELLIGENCE_UNAVAILABLE"})
    search_check(search_degraded)
    for topK in (1, 10):
        boundary = copy.deepcopy(search)
        boundary["request"]["topK"] = topK
        search_check(boundary)
    # Ranking scale is engine-specific, with no arbitrary probability bounds.
    for score in (-2.5, 12.0):
        ranked = copy.deepcopy(search)
        ranked["expected"]["results"][0]["score"] = score
        search_check(ranked)
    replacement = copy.deepcopy(search)
    replacement["request"]["profile"] = replacement["expected"]["profile"] = "room-request"
    replacement["expected"]["runtime"].update(engine="illustrative-bm25", engineVersion="illustrative@0.2.0", profileVersion="room-request@0.1.0")
    search_check(replacement)
    print("PASS 4 schemas, 5 categories, 16 priority cells, 12 annotated tickets, triage degraded, 5 knowledge digests and dataset digest")
    print("PASS shared contract domain isolation, profile file/version pins and single policy matrix/fallback authority")
    print("PASS search SUCCESS/hit, empty SUCCESS, DEGRADED, topK boundaries, ranking scales and 12 retrieval-gold associations (no engine run)")

    def fixture_check(value):
        validate_fixture(value, profile, taxonomy, rules, policy, request_validator, response_validator)

    mutations = []
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["evidence"][0]["text"] = "invented evidence"; mutations.append(("fabricated evidence", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["workspaceId"] = "lab-b"; mutations.append(("workspace echo mismatch", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["result"]["priority"] = "CRITICAL"; mutations.append(("priority mismatch", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["result"]["suggestedGroup"] = "IT-ACCOUNT"; mutations.append(("routing mismatch", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["explanation"]["matchedRules"].append("UNKNOWN-RULE"); mutations.append(("unknown rule", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["runtime"]["rulesVersion"] = "9.0.0"; mutations.append(("stale rules version", bad, fixture_check))
    bad = copy.deepcopy(fixtures[10]); bad["expected"]["needsReview"] = False; mutations.append(("ambiguous review disabled", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]["expected"]); bad["result"] = None; mutations.append(("success with null result", bad, response_validator.validate))
    bad = copy.deepcopy(fixtures[0]["expected"]); bad["confidence"] = 0.93; mutations.append(("uncontracted confidence", bad, response_validator.validate))
    bad = copy.deepcopy(degraded); bad["needsReview"] = False; mutations.append(("degraded review disabled", bad, response_validator.validate))
    bad = copy.deepcopy(degraded); bad["result"] = fixtures[0]["expected"]["result"]; mutations.append(("degraded fabricated result", bad, response_validator.validate))
    bad = copy.deepcopy(fixtures[0]["request"]); bad["input"]["title"] = "   "; mutations.append(("blank input", bad, request_validator.validate))
    bad = copy.deepcopy(knowledge); bad["artifacts"][0]["checksum"] = "0" * 64; mutations.append(("stale knowledge checksum", bad, validate_knowledge))
    bad = copy.deepcopy(knowledge); bad["artifacts"][0]["metadataId"] = "fake-core-id"; mutations.append(("fabricated Core binding", bad, validate_knowledge))
    bad = copy.deepcopy(knowledge); bad["artifacts"][0]["usageRights"] = "CC0-1.0"; mutations.append(("unapproved knowledge license", bad, validate_knowledge))
    for key in ("owner", "roles", "permissions", "isAdmin", "authorizationGranted"):
        bad = copy.deepcopy(fixtures[0]); bad["request"]["input"][key] = True; mutations.append((f"untrusted input {key}", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["request"]["workspaceId"] = "lab-b"; mutations.append(("legacy client workspace", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["request"]["context"]["authorizationGranted"] = True; mutations.append(("context permission grant", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["evidence"][0]["type"] = "KNOWLEDGE"; mutations.append(("knowledge in triage evidence", bad, fixture_check))
    bad = copy.deepcopy(fixtures[0]); bad["expected"]["evidence"][0]["metadataId"] = search["expected"]["results"][0]["metadataId"]; mutations.append(("provenance in input evidence", bad, fixture_check))
    bad = copy.deepcopy(policy); bad["policyVersion"] = "priority-policy@9.0.0"; mutations.append(("priority policy version drift", bad, lambda value: validate_profile(profile, taxonomy, rules, value)))
    bad = copy.deepcopy(policy); bad["owner"] = "Intelligence"; mutations.append(("priority policy owner drift", bad, lambda value: validate_profile(profile, taxonomy, rules, value)))
    bad = copy.deepcopy(search); bad["request"]["query"] = "   "; mutations.append(("blank search query", bad, search_check))
    for topK in (0, 11, 1.5, True):
        bad = copy.deepcopy(search); bad["request"]["topK"] = topK; mutations.append((f"invalid topK {topK}", bad, search_check))
    for key in ("owner", "roles", "permissions", "isAdmin", "authorizationGranted", "jwt", "filters"):
        bad = copy.deepcopy(search); bad["request"][key] = "untrusted"; mutations.append((f"search request {key}", bad, search_check))
    bad = copy.deepcopy(search); del bad["request"]["context"]; mutations.append(("missing trusted search context", bad, search_check))
    bad = copy.deepcopy(search_degraded); bad["expected"]["results"] = search["expected"]["results"]; mutations.append(("degraded search has hits", bad, search_check))
    bad = copy.deepcopy(search_degraded); bad["expected"]["needsReview"] = False; mutations.append(("degraded search review disabled", bad, search_check))
    bad = copy.deepcopy(search_degraded); bad["expected"]["runtime"] = search["expected"]["runtime"]; mutations.append(("degraded search has runtime", bad, search_check))
    bad = copy.deepcopy(search_degraded); bad["expected"]["error"]["code"] = "UNAUTHORIZED"; mutations.append(("authorization as degraded search", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["runtime"] = None; mutations.append(("success search null runtime", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["error"] = {"code": "INTELLIGENCE_UNAVAILABLE"}; mutations.append(("success search has error", bad, search_check))
    for field in ("workspaceId", "requestId", "profile", "contractVersion"):
        bad = copy.deepcopy(search); bad["expected"][field] = "mismatch"; mutations.append((f"search envelope {field} mismatch", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["results"][0]["workspaceId"] = "lab-b"; mutations.append(("search hit workspace mismatch", bad, search_check))
    for metadataId in ("malformed-uuid", "kb-network-001"):
        bad = copy.deepcopy(search); bad["expected"]["results"][0]["metadataId"] = metadataId; mutations.append((f"invalid runtime citation {metadataId}", bad, search_check))
    for checksum in ("A" * 64, "a" * 63, "a" * 65, "g" * 64, "a" * 64 + "\n"):
        bad = copy.deepcopy(search); bad["expected"]["results"][0]["sourceChecksum"] = checksum; mutations.append(("invalid search checksum", bad, search_check))
    for field in ("engineVersion", "profileVersion", "corpusVersion", "indexVersion"):
        bad = copy.deepcopy(search); del bad["expected"]["runtime"][field]; mutations.append((f"missing search {field}", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["confidence"] = 0.8; mutations.append(("search envelope confidence", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["results"][0]["confidence"] = 0.8; mutations.append(("search hit confidence", bad, search_check))
    bad = copy.deepcopy(search); bad["expected"]["results"][0]["evidence"] = {"type": "INPUT"}; mutations.append(("input evidence in search hit", bad, search_check))
    for score in (float("nan"), float("inf"), float("-inf")):
        bad = copy.deepcopy(search); bad["expected"]["results"][0]["score"] = score; mutations.append(("non-finite search score", bad, search_check))
    bad = copy.deepcopy(search); bad["request"]["topK"] = 1; bad["expected"]["results"] *= 2; mutations.append(("search hit count exceeds topK", bad, search_check))
    bad = copy.deepcopy(gold); bad["cases"][0]["expectedRelevantLocalArtifacts"] = ["kb-unknown-001"]; mutations.append(("unknown retrieval-gold artifact", bad, lambda value: validate_retrieval_gold(value, fixtures, knowledge)))
    bad = copy.deepcopy(gold); bad["benchmarkStatus"] = "BENCHMARK"; mutations.append(("retrieval-gold benchmark claim", bad, lambda value: validate_retrieval_gold(value, fixtures, knowledge)))

    # Structural regression cases for the repository-level contracts and split profile.
    def schema_check(value):
        validate_shared_schema(value, domain_literals)

    for dialect in (None, "http://json-schema.org/draft-07/schema#"):
        bad = copy.deepcopy(schemas["triage-result.schema.json"])
        if dialect is None:
            del bad["$schema"]
        else:
            bad["$schema"] = dialect
        mutations.append(("missing/wrong schema dialect", bad, schema_check))
    for literal in sorted(domain_literals):
        for keyword in ("enum", "const", "default"):
            bad = copy.deepcopy(schemas["triage-result.schema.json"])
            bad["$defs"]["domainLeak"] = {keyword: [literal] if keyword == "enum" else literal}
            mutations.append((f"shared schema {keyword} {literal}", bad, schema_check))
    bad = copy.deepcopy(schemas["search-result.schema.json"])
    bad["$defs"]["domainLeak"] = {"const": {"category": "NETWORK"}}
    mutations.append(("shared schema object constant domain leak", bad, schema_check))

    def profile_check(value):
        validate_profile(value, taxonomy, rules, policy)

    def taxonomy_check(value):
        validate_profile(profile, value, rules, policy)

    def rules_check(value):
        validate_profile(profile, taxonomy, value, policy)

    def policy_check(value):
        validate_profile(profile, taxonomy, rules, value)

    for section in ("taxonomy", "rules", "priorityPolicy", "normalization"):
        bad = copy.deepcopy(profile)
        bad[section]["file"] = "../outside-profile.yaml"
        mutations.append((f"profile {section} file escape", bad, profile_check))
        bad = copy.deepcopy(profile)
        bad[section]["version"] = "9.0.0"
        mutations.append((f"profile {section} version pin drift", bad, profile_check))
    bad = copy.deepcopy(profile)
    bad["engine"] = "tfidf"
    mutations.append(("unimplemented engine in profile", bad, profile_check))
    bad = copy.deepcopy(taxonomy)
    bad["categories"].pop("NETWORK")
    mutations.append(("missing taxonomy category", bad, taxonomy_check))
    bad = copy.deepcopy(taxonomy)
    bad["priorityMatrix"] = policy["matrix"]
    mutations.append(("policy concern in taxonomy", bad, taxonomy_check))
    bad = copy.deepcopy(rules)
    bad["categoryRules"]["CAT-NET-001"]["category"] = "UNKNOWN"
    mutations.append(("rule references unknown category", bad, rules_check))
    bad = copy.deepcopy(rules)
    bad["categoryRules"]["CAT-NET-001"]["positiveSignals"] = []
    mutations.append(("empty category signals", bad, rules_check))
    bad = copy.deepcopy(rules)
    bad["impactRules"]["I1"] = "CAT-NET-001"
    mutations.append(("duplicate catalogue rule ID", bad, rules_check))
    bad = copy.deepcopy(rules)
    bad["reviewRules"]["REVIEW-INSUFFICIENT-001"]["policyRef"] = "taxonomy.yaml#/fallback"
    mutations.append(("review rule points outside policy fallback", bad, rules_check))
    bad = copy.deepcopy(policy)
    bad["matrix"]["I1"].pop("U1")
    mutations.append(("incomplete priority matrix", bad, policy_check))
    bad = copy.deepcopy(policy)
    bad["matrix"]["I1"]["U1"] = "CRITICAL"
    mutations.append(("changed priority matrix cell", bad, policy_check))
    bad = copy.deepcopy(policy)
    bad["reviewFallback"]["impact"] = "I1"
    mutations.append(("invented impact in review fallback", bad, policy_check))
    bad = copy.deepcopy(policy)
    bad["reviewFallback"]["needsReview"] = False
    mutations.append(("policy fallback disables review", bad, policy_check))
    bad = copy.deepcopy(knowledge)
    bad["artifacts"][0]["byteSize"] += 1
    mutations.append(("wrong exact-byte source size", bad, validate_knowledge))
    bad = copy.deepcopy(knowledge)
    bad["artifacts"][0]["workspaceId"] = "lab-a"
    mutations.append(("fabricated fixture workspace binding", bad, validate_knowledge))
    bad = copy.deepcopy(knowledge)
    bad["artifacts"][0]["ingestionStatus"] = "INGESTED"
    mutations.append(("fixture claims ingestion", bad, validate_knowledge))
    mutations.append(("artifact outside fixture boundary", "README.md", local_artifact))
    mutations.append(("artifact traversal outside repository", "../outside.md", local_artifact))
    mutations.append(("duplicate JSON key", '{"id": 1, "id": 2}',
                      lambda value: json.loads(value, object_pairs_hook=unique_pairs)))
    mutations.append(("duplicate YAML key", "status: PROPOSED\nstatus: ADOPTED\n",
                      lambda value: yaml.load(value, Loader=UniqueYamlLoader)))
    mutations.append(("non-JSON numeric constant", '{"score": NaN}',
                      lambda value: json.loads(value, parse_constant=reject_json_constant)))
    for label, value, validator in mutations:
        reject_mutation(label, value, validator)
    # Generic schema must permit another profile without Service Desk enums.
    other = copy.deepcopy(fixtures[0]["expected"])
    other.update(profile="room-request")
    other["result"].update(category="ROOM_BOOKING", priority="NORMAL", suggestedGroup="ROOM-COORDINATOR")
    other["runtime"]["profileVersion"] = "room-request@0.1.0"
    response_validator.validate(other)
    model = copy.deepcopy(fixtures[0]["expected"])
    model["runtime"] = {"engine": "classifier", "profileVersion": "service-desk@0.1.0", "modelVersion": "illustrative-model@0.1.0"}
    model["explanation"]["matchedRules"] = []
    response_validator.validate(model)
    print(f"PASS {len(mutations)} negative cases, generic profile reuse, triage model and search engine schema substitution (no engine run)")
    print("Tools:", sys.version.split()[0], "PyYAML", importlib.metadata.version("PyYAML"), "jsonschema", importlib.metadata.version("jsonschema"))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FAIL {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
