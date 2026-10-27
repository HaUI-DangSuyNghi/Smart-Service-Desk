"""E5 offline image verification; no user content, credentials or network access."""
import re
from full_model_release import docker, inspect, require

MODEL = "intfloat/multilingual-e5-small"
REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
ARTIFACT_DIGEST = "sha256:8f5c24508c0307b029da65796be624c0a3345be7f5b96622a04e0b6285bc53fb"
FILES = (
    ("onnx/model.onnx", 470268510, "ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665"),
    ("onnx/tokenizer.json", 17082730, "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39"),
)
SCHEMA = "ai-d2-e5-image@1.0.0"


def validate_receipt(value):
    require(type(value) is dict and set(value) == {
        "schemaVersion", "model", "revision", "artifactDigest", "imageId", "parentImageId", "files",
    }, "Unrecognized E5 receipt")
    require((value["schemaVersion"], value["model"], value["revision"], value["artifactDigest"]) ==
            (SCHEMA, MODEL, REVISION, ARTIFACT_DIGEST), "E5 model/version/digest is not approved")
    require(set(value["files"]) == {item[0] for item in FILES}, "Incomplete E5 artifact inventory")
    for name, size, sha in FILES:
        require(value["files"][name] == {"size": size, "sha256": sha}, "E5 artifact pin mismatch")
    for name in ("imageId", "parentImageId"):
        require(type(value[name]) is str and re.fullmatch(r"sha256:[0-9a-f]{64}", value[name]),
                "Unpinned E5 Docker image identity")
    return value


def verify_image(image, receipt, *, inference=True):
    validate_receipt(receipt)
    meta = inspect(image)
    require(meta["Id"] == receipt["imageId"] and meta["Os"] == "linux"
            and meta["Architecture"] == "amd64", "E5 image identity/platform mismatch")
    labels = meta["Config"].get("Labels") or {}
    require(labels.get("vn.haui.ssd.e5-model") == MODEL
            and labels.get("vn.haui.ssd.e5-revision") == REVISION
            and labels.get("vn.haui.ssd.e5-artifact-digest") == ARTIFACT_DIGEST
            and labels.get("vn.haui.ssd.e5-parent-image-id") == receipt["parentImageId"],
            "E5 image labels do not match signed receipt")
    require(meta["Config"]["User"] == "10001:10001", "E5 image must retain unprivileged runtime")
    if inference:
        # The provider verifies both model files' pinned size/hash and the exact ONNX dependency set.
        script = ("from hdpi.retrieval.dense.provider import OnnxEmbeddingProvider; "
                  "p=OnnxEmbeddingProvider('/opt/models/e5'); "
                  "a=p.encode_query('synthetic support ticket'); "
                  "b=p.encode_documents(('synthetic support ticket',)); "
                  "assert len(a)==384 and len(b)==1 and len(b[0])==384; "
                  "print('E5_OFFLINE_SMOKE_PASS')")
        output = docker("run", "--rm", "--network", "none", "--read-only", "--memory", "2g",
                        "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                        "--entrypoint", "python", image, "-c", script)
        require(output.strip() == "E5_OFFLINE_SMOKE_PASS", "E5 offline inference failed")
    return meta
