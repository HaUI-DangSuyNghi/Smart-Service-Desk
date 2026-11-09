FROM python:3.12.16-slim-trixie@sha256:5e2bdb1b0d542d5039cffb8181c65930093949194a65c365dfb1b0fe13a4fa5b AS build
WORKDIR /build
COPY intelligence/requirements.lock intelligence/requirements.lock
RUN pip install --no-cache-dir -r intelligence/requirements.lock \
    && pip wheel --no-cache-dir --no-deps -r intelligence/requirements.lock -w /wheels
COPY intelligence/ intelligence/
COPY contracts/intelligence/v1/ contracts/intelligence/v1/
RUN pip wheel --no-cache-dir --no-build-isolation --no-deps ./intelligence -w /wheels

FROM python:3.12.16-slim-trixie@sha256:5e2bdb1b0d542d5039cffb8181c65930093949194a65c365dfb1b0fe13a4fa5b
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade zlib1g=1:1.3.dfsg+really1.3.1-1+deb13u1 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels /wheels/smart_service_intelligence-*.whl \
    && rm -rf /wheels
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "-m", "hdpi.http_service", "--host", "0.0.0.0"]
