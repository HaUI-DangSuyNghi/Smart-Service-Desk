"""Application contract alignment; no application values enter the generic wheel."""

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]


def test_openapi_triage_profile_matches_active_application_pin():
    api = yaml.safe_load(
        (ROOT / "contracts/openapi/servicedesk.yaml").read_text("utf-8")
    )
    versions = api["components"]["schemas"]["TriageResponse"]["properties"]["runtime"][
        "properties"
    ]["profileVersion"]["enum"]
    adapter = (
        ROOT
        / "backend/src/main/java/vn/haui/dx/apps/servicedesk/triage/TicketTriageService.java"
    ).read_text("utf-8")

    assert versions == ["service-desk@0.3.0"]
    assert 'new TriageInput("service-desk", "0.3.0"' in adapter


def test_public_assistance_cannot_accept_profile_model_or_source_overrides():
    api = yaml.safe_load(
        (ROOT / "contracts/openapi/servicedesk.yaml").read_text("utf-8")
    )
    question = api["components"]["schemas"]["AssistanceQuestion"]

    assert question["additionalProperties"] is False
    assert question["required"] == ["question"]
    assert set(question["properties"]) == {"question"}
    assert question["properties"]["question"]["maxLength"] == 2000
    assert api["components"]["schemas"]["AssistanceResponse"]["properties"][
        "needsReview"
    ]["enum"] == [True]
