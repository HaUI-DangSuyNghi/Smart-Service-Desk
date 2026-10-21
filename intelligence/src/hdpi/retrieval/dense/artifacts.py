"""Reviewed upstream artifact pins; model bytes are provisioned outside the wheel."""
from ..models import digest

MODEL = 'intfloat/multilingual-e5-small'
REVISION = '614241f622f53c4eeff9890bdc4f31cfecc418b3'
FILES = (
    ('onnx/model.onnx', 470268510, 'ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665'),
    ('onnx/tokenizer.json', 17082730, '0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39'),
)
ARTIFACT_DIGEST = 'sha256:' + digest([MODEL, REVISION, FILES])
