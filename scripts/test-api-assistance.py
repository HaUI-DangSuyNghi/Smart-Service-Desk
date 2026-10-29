"""Exercise opt-in localhost API Q&A on a synthetic ticket; never print credentials or answers."""

import argparse
import json
from pathlib import Path
import uuid

import runpy

HELPERS = runpy.run_path(str(Path(__file__).with_name("test-local-model.py")))
request, login, require = (HELPERS[name] for name in ("request", "login", "require"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "ticket", type=uuid.UUID, help="Existing synthetic fixture owned by user1"
    )
    parser.add_argument("--expect-unavailable", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    config = dict(
        line.split("=", 1)
        for line in (HELPERS["ROOT"] / ".env")
        .read_text(encoding="utf-8-sig")
        .splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    token = login("user1", config["DEMO_PASSWORD"])
    code, status = request("/workspaces/lab-a/intelligence/status", token)
    require(
        code == 200 and status["assistanceMode"] == "api",
        "API assistance is not configured (HTTP "
        + str(code)
        + ", mode="
        + str((status or {}).get("assistanceMode"))
        + ")",
    )
    path = "/workspaces/lab-a/tickets/" + str(args.ticket)
    code, before = request(path, token)
    require(code == 200, "Synthetic ticket must be readable")
    question = {
        "question": "Máy không lên nguồn và tôi cần thi gấp. Tôi nên kiểm tra gì trước?"
    }
    require(
        request(path + "/assistance", body=question)[0] == 401,
        "Anonymous assistance must be denied",
    )
    code, result = request(path + "/assistance", token, question)
    if args.expect_unavailable:
        require(code in (503, 504), "Provider outage must be reported explicitly")
        proof = {"result": "OUTAGE_HANDLED", "httpStatus": code}
    else:
        require(
            code == 200
            and result["contractVersion"] == "0.2.0"
            and result["status"] == "SUCCESS",
            "API assistance did not answer",
        )
        require(
            result["reasonCode"] == "MODEL_ADVICE"
            and result["needsReview"]
            and result["citations"] == [],
            "Advice must not fabricate grounded citations",
        )
        require(
            isinstance(result["answer"], str) and bool(result["answer"].strip()),
            "Empty model answer",
        )
        foreign = login("user4", config["DEMO_PASSWORD"])
        require(
            request(path + "/assistance", foreign, question)[0] in (403, 404),
            "Foreign workspace must not call AI for this ticket",
        )
        require(
            request(
                path + "/assistance",
                token,
                {**question, "provider": "client-controlled"},
            )[0]
            == 400,
            "Only question may be supplied by browser",
        )
        after = request(path, token)[1]
        require(
            before == after,
            "Question answering must not mutate ticket or stored classification",
        )
        proof = {
            "result": "PASS",
            "authenticatedQuestion": True,
            "modelAdvice": True,
            "fabricatedCitations": False,
            "crossWorkspaceDenied": True,
            "classificationUnchanged": True,
        }
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(proof))


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        raise SystemExit(str(error)) from None
    except Exception as error:
        raise SystemExit(
            "API Q&A verification failed: " + type(error).__name__
        ) from None
