FROM python:3.12.12-slim-bookworm AS build
WORKDIR /build
COPY intelligence/requirements.lock intelligence/requirements.lock
RUN pip install --no-cache-dir -r intelligence/requirements.lock \
    && pip wheel --no-cache-dir --no-deps -r intelligence/requirements.lock -w /wheels
COPY intelligence/ intelligence/
COPY contracts/intelligence/v1/ contracts/intelligence/v1/
RUN pip wheel --no-cache-dir --no-build-isolation --no-deps ./intelligence -w /wheels

FROM python:3.12.12-slim-bookworm
COPY --from=build /wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels /wheels/smart_service_intelligence-*.whl \
    && rm -rf /wheels
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "-m", "hdpi.http_service", "--host", "0.0.0.0"]
