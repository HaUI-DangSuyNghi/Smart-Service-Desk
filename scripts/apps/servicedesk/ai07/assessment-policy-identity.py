"""In danh tính byte của bộ chính sách assessment để bên cấp grant đối chiếu."""

from hashlib import sha256
import json

from _bootstrap import ROOT


def main():
    resources = ROOT / "backend/src/main/resources/apps/servicedesk/intelligence"
    artifacts = {
        "taxonomyDigest": "taxonomy-v0.1.json",
        "assessmentPolicyDigest": "assessment-policy-v0.1.json",
        "priorityPolicyDigest": "triage-policy.json",
    }
    identity = {
        key: sha256((resources / name).read_bytes()).hexdigest()
        for key, name in artifacts.items()
    }
    identity["assessmentPolicyBundleDigest"] = sha256(
        ":".join(identity.values()).encode("utf-8")
    ).hexdigest()
    print(json.dumps(identity, indent=2))


if __name__ == "__main__":
    main()
