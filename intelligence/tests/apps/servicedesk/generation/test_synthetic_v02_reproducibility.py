from copy import deepcopy

import pytest

from _contracts import (
    DATA,
    SEED,
    DatasetError,
    canonical,
    default_inputs,
    digest,
    load_inputs,
    read_json,
    write_json,
)
from _dataset import generate_bundle
from _validation import default_bundle_paths, validate_bundle


def test_two_runs_and_committed_artifacts_have_identical_canonical_bytes(
    pristine_bundle, tmp_path
):
    paths = default_bundle_paths()
    first = generate_bundle()
    second = generate_bundle()

    for name, value in first.items():
        left = tmp_path / "first" / (name + ".json")
        right = tmp_path / "second" / (name + ".json")
        write_json(left, value)
        write_json(right, second[name])
        assert left.read_bytes() == right.read_bytes() == paths[name].read_bytes()
        assert b"\r\n" not in left.read_bytes()

    assert validate_bundle(pristine_bundle)["caseCount"] == 400


def test_seed_drift_and_allocation_drift_are_rejected(tmp_path):
    with pytest.raises(DatasetError, match="SEED_MISMATCH"):
        generate_bundle(seed=SEED + 1)

    inputs = default_inputs()
    plan = read_json(inputs["plan"])
    plan["categories"][0]["newCount"] -= 1
    plan["categories"][1]["newCount"] += 1
    changed = tmp_path / "plan.json"
    write_json(changed, plan)

    with pytest.raises(DatasetError):
        load_inputs({**inputs, "plan": changed})


def test_fact_change_requires_new_pin_and_changes_replayed_dataset(
    tmp_path, pristine_bundle
):
    inputs = default_inputs()
    facts = read_json(inputs["facts"])
    facts["families"][0]["facts"][0][
        "statementEn"
    ] += " This revised fictional note stays provisional."
    changed_facts = tmp_path / "facts.json"
    write_json(changed_facts, facts)

    with pytest.raises(DatasetError, match="INPUT_PIN_FACTS"):
        generate_bundle({**inputs, "facts": changed_facts})

    plan = read_json(inputs["plan"])
    plan["factsDigest"] = digest(changed_facts.read_bytes())
    changed_plan = tmp_path / "plan.json"
    write_json(changed_plan, plan)
    changed_inputs = {**inputs, "facts": changed_facts, "plan": changed_plan}
    replay = generate_bundle(changed_inputs)
    assert digest(replay["dataset"]) != digest(pristine_bundle["dataset"])

    with pytest.raises(DatasetError):
        validate_bundle(pristine_bundle, changed_inputs)


def test_duplicate_template_and_external_fact_reference_fail_input_shape(tmp_path):
    inputs = default_inputs()
    templates = read_json(inputs["templates"])
    templates["templates"][1]["id"] = templates["templates"][0]["id"]
    changed_templates = tmp_path / "templates.json"
    write_json(changed_templates, templates)
    plan = read_json(inputs["plan"])
    plan["templatesDigest"] = digest(changed_templates.read_bytes())
    changed_plan = tmp_path / "plan.json"
    write_json(changed_plan, plan)

    with pytest.raises(DatasetError, match="DUPLICATE_TEMPLATE"):
        load_inputs({**inputs, "templates": changed_templates, "plan": changed_plan})

    facts = read_json(inputs["facts"])
    facts["externalSource"] = "unapproved-input"
    changed_facts = tmp_path / "facts.json"
    write_json(changed_facts, facts)

    with pytest.raises(DatasetError):
        load_inputs({**inputs, "facts": changed_facts})
