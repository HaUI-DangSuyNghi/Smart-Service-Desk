FROM python:3.12.7-slim@sha256:60d9996b6a8a3689d36db740b49f4327be3be09a21122bd02fb8895abb38b50d

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY wheels/*.whl /wheels/
RUN python -m pip install --no-cache-dir --no-deps /wheels/*.whl \
    && rm -rf /wheels

WORKDIR /opt/app
COPY local_classification/ ./local_classification/
COPY bridge-revision /opt/bridge-revision

USER 10001:10001
ENTRYPOINT ["python", "-m", "local_classification.bridge_api"]
