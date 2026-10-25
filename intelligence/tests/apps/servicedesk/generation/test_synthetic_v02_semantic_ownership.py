from pathlib import Path
import zipfile

import pytest

from _contracts import ROOT
from _ownership import audit_delta, audit_repository, audit_wheel, semantic_violations


@pytest.mark.parametrize(
    "source",
    [
        "from apps.servicedesk import policy",
        "import apps.servicedesk",
        "load('fixtures/intelligence/service-desk/generation/dev-v0.2.json')",
        "path = Path('fixtures') / 'intelligence' / 'service-desk'",
        "value = importlib.resources.files('apps').joinpath('servicedesk')",
        "profile = 'service-' + 'desk'",
        "alias = 'wi-fi'",
        "category = 'data_exfiltration_canary'",
        "prompt = 'Service-desk knowledge policy default'",
    ],
)
def test_app_import_literal_and_resource_mutations_in_new_shared_code_are_rejected(
    source,
):
    assert semantic_violations(source)
    assert audit_delta("scripts/intelligence/new_probe.py", source)
    assert audit_delta("intelligence/tests/generation/new_probe.py", source)


def test_delta_gate_preserves_existing_debt_without_permitting_new_dependencies():
    old = "profile = 'service-desk'\n"
    assert not audit_delta("scripts/intelligence/legacy.py", old, old)
    changed = (
        old
        + "dataset = 'fixtures/intelligence/service-desk/generation/dev-v0.2.json'\n"
    )
    assert audit_delta("scripts/intelligence/legacy.py", changed, old)
    assert audit_delta("intelligence/src/hdpi/probe.py", old, old)


def test_active_egress_bypass_and_private_core_imports_are_rejected():
    assert semantic_violations(
        "parser.add_argument('--skip-approval', action='store_true')", generic=False
    )
    assert semantic_violations("from hdpi._resources import policy", generic=False)
    assert semantic_violations("from core.identity import Directory", generic=False)
    assert not semantic_violations(
        "from hdpi.evaluation.generation import evaluate_generation", generic=False
    )


def test_written_source_mutation_is_detected_by_repository_audit(tmp_path):
    path = tmp_path / "intelligence/src/hdpi/probe.py"
    path.parent.mkdir(parents=True)
    path.write_text("DEFAULT = 'service-desk'\n", encoding="utf-8")
    result = audit_repository(tmp_path)
    assert result["result"] == "FAIL"
    assert any(item["rule"] == "APP_SEMANTIC_LITERAL" for item in result["violations"])


def make_wheel(path, extra=None):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("hdpi/__init__.py", "")
        archive.writestr(
            "smart_service_intelligence-0.3.0.dist-info/METADATA",
            "Name: smart-service-intelligence\nVersion: 0.3.0\n",
        )

        for schema in (ROOT / "contracts/intelligence/v1").iterdir():
            member = "hdpi/_resources/contracts/" + schema.name

            if extra and extra[0] == member:
                continue

            archive.writestr(member, schema.read_bytes())

        if extra:
            archive.writestr(*extra)


@pytest.mark.parametrize(
    "member, content",
    [
        ("apps/servicedesk/source-facts-v0.2.json", "{}"),
        ("hdpi/_resources/contracts/source-facts-v0.2.json", "{}"),
        ("hdpi/opaque.txt", "app policy"),
        ("hdpi/_resources/contracts/contract.md", "changed contract"),
        ("hdpi/probe.py", "profile = 'service-desk'"),
        ("hdpi/_resources/contracts/probe.schema.json", '{"enum":["role_spoofing"]}'),
    ],
)
def test_built_wheel_member_and_resource_mutations_fail(tmp_path, member, content):
    clean = tmp_path / "clean.whl"
    make_wheel(clean)
    assert audit_wheel(clean)["result"] == "PASS"
    changed = tmp_path / "changed.whl"
    make_wheel(changed, (member, content))
    assert audit_wheel(changed)["result"] == "FAIL"


def test_empty_or_wrong_distribution_cannot_pass_wheel_gate(tmp_path):
    path = tmp_path / "empty.whl"

    with zipfile.ZipFile(path, "w"):
        pass

    assert audit_wheel(path)["result"] == "FAIL"
