FROM apache/apisix:3.19.0-ubuntu@sha256:bb4958b943949b3543353481eff1333e10b3323c6095dd0af4406de2b8cf738e
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade \
        apt=2.8.4ubuntu0.1 libapt-pkg6.0t64=2.8.4ubuntu0.1 \
        libssl3t64=3.0.13-0ubuntu3.16 openssl=3.0.13-0ubuntu3.16 \
    && rm -rf /var/lib/apt/lists/*
USER apisix
