FROM python:3.12.7-slim
WORKDIR /opt/hdpi
COPY intelligence/pyproject.toml intelligence/setup.py intelligence/MANIFEST.in intelligence/requirements.lock intelligence/.python-version ./intelligence/
COPY intelligence/src ./intelligence/src
COPY contracts/intelligence/v1 ./contracts/intelligence/v1
RUN python -m pip install --no-cache-dir -r intelligence/requirements.lock && python -m pip install --no-cache-dir --no-deps --no-build-isolation ./intelligence
ENV PYTHONDONTWRITEBYTECODE=1
USER 10001:10001
CMD ["python", "-I", "-m", "hdpi_intelligence.http_service", "--host", "0.0.0.0", "--profiles-root", "/profiles"]
