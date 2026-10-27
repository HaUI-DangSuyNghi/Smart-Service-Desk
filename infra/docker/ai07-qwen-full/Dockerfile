# Builder creates a content-named local alias and verifies its inspected image ID.
ARG CODE_IMAGE_REF=ssd-ai07-code-pinned:required
FROM ${CODE_IMAGE_REF}
USER root
COPY --chown=10001:10001 model/ /opt/model/
COPY --chown=10001:10001 apps/ /opt/apps/
COPY --chown=10001:10001 licenses/ /opt/licenses/
ENV PYTHONPATH=/opt/apps HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 HF_HUB_DISABLE_TELEMETRY=1 DO_NOT_TRACK=1 HOME=/tmp/qwen
ARG CODE_IMAGE_ID
ARG MODEL_DIGEST
ARG POLICY_DIGEST
LABEL vn.haui.ssd.code-image-id=${CODE_IMAGE_ID} vn.haui.ssd.model-digest=${MODEL_DIGEST} vn.haui.ssd.policy-digest=${POLICY_DIGEST}
USER 10001:10001
WORKDIR /tmp
