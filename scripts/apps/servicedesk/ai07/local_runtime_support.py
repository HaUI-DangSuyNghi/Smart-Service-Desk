"""Operator-only Docker evidence helpers; never emit prompts, keys or raw model output."""

import json
import subprocess
import sys

from _bootstrap import ROOT


sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from local_classification.ollama_transport import strict_json  # noqa: E402


class WorkerRejected(ValueError):
    def __init__(self, status):
        self.status = status
        super().__init__("Local inference attempt rejected")


def docker_json(*arguments):
    result = subprocess.run(["docker", *arguments], check=True, capture_output=True)
    return strict_json(result.stdout.strip(), 262144)


def worker(container, *, candidate=None, fields=None):
    command = [
        "docker",
        "exec",
        "-i",
        container,
        "python",
        "-m",
        "local_classification.worker",
    ]

    if candidate is None:
        command.append("--identity")
    else:
        command += ["--candidate", candidate]

    raw = (
        None
        if fields is None
        else json.dumps(fields, ensure_ascii=False).encode("utf-8")
    )
    result = subprocess.run(command, input=raw, capture_output=True, timeout=150)

    if result.returncode:
        status = 0
        try:
            status = strict_json(result.stderr.strip())["status"]
        except (ValueError, KeyError):
            pass
        raise WorkerRejected(status)

    return strict_json(result.stdout.strip())


def deployment_evidence(container):
    bridge = docker_json("inspect", container)[0]
    mode = bridge["HostConfig"]["NetworkMode"]

    if not mode.startswith("container:"):
        raise ValueError("Bridge is not collocated in the inference namespace")

    model = docker_json("inspect", mode.split(":", 1)[1])[0]

    for service in (bridge, model):
        host = service["HostConfig"]

        if (
            not host["ReadonlyRootfs"]
            or host["LogConfig"]["Type"] != "none"
            or host.get("PortBindings")
            or "ALL" not in host["CapDrop"]
            or not any("no-new-privileges" in value for value in host["SecurityOpt"])
        ):
            raise ValueError("Inference container hardening differs")

        mounts = {item["Destination"]: item for item in service["Mounts"]}

        if "/models" not in mounts or mounts["/models"]["RW"]:
            raise ValueError("Runtime model volume is not read-only")

        allowed = {
            "/models",
            "/run/config/runtime.json",
            "/run/policies",
            "/run/secrets/ai07-token",
        }

        if any(
            item["Destination"] not in allowed and item["Destination"] != "/tmp"
            for item in service["Mounts"]
        ):
            raise ValueError("Unexpected inference mount")

    environment = dict(
        value.split("=", 1) for value in model["Config"]["Env"] if "=" in value
    )

    if environment.get("OLLAMA_HOST") != "127.0.0.1:11434":
        raise ValueError("Raw model API is not loopback-only")

    networks = model["NetworkSettings"]["Networks"]
    if len(networks) != 1:
        raise ValueError("Inference must have one isolated network")

    network = docker_json(
        "network", "inspect", next(iter(networks.values()))["NetworkID"]
    )[0]
    if network["Internal"] is not True:
        raise ValueError("Inference network permits external routing")

    probe = """
import http.client
import json
import os
import socket

connection = http.client.HTTPConnection("127.0.0.1", 8002, timeout=5)
connection.request("GET", "/v1/identity")
response = connection.getresponse()
denied = response.status == 403
response.read()
connection.close()

reachable = False
try:
    connection = socket.create_connection(("1.1.1.1", 443), timeout=2)
    reachable = True
    connection.close()
except OSError:
    pass

print(
    json.dumps(
        {
            "unauthenticatedDenied": denied,
            "externalReachable": reachable,
            "readOnlyMount": bool(os.statvfs("/models").f_flag & os.ST_RDONLY),
        }
    )
)
"""
    probe_result = subprocess.run(
        ["docker", "exec", "-i", container, "python", "-"],
        input=probe.encode(),
        capture_output=True,
        check=True,
        timeout=10,
    )
    observed = strict_json(probe_result.stdout.strip())

    if observed != {
        "unauthenticatedDenied": True,
        "externalReachable": False,
        "readOnlyMount": True,
    }:
        raise ValueError("Live inference isolation probe failed")

    return {
        "bridgeImageId": bridge["Image"],
        "ollamaImageId": model["Image"],
        "ollamaImageRef": model["Config"]["Image"],
        "privateNetwork": True,
        "publishedPorts": [],
        "readOnlyModelVolume": True,
        "bodyLoggingDisabledByDocker": True,
        "liveProbes": observed,
    }


def gpu_identity():
    result = subprocess.run(
        [
            "nvidia-smi",
            "--query-gpu=name,driver_version,memory.total",
            "--format=csv,noheader,nounits",
        ],
        capture_output=True,
        check=True,
        text=True,
        timeout=5,
    )
    rows = []

    for line in result.stdout.splitlines():
        name, driver, memory = (item.strip() for item in line.split(","))
        rows.append({"name": name, "driverVersion": driver, "totalMiB": int(memory)})

    return rows


def rule_baseline(cases):
    from hashlib import sha256
    from hdpi.domain.models import TrustedContext
    from hdpi.profiles.registry import ProfileRegistry
    from hdpi.services.triage import TriageService

    profiles = ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
    service = TriageService(registry=ProfileRegistry(root=profiles))
    results = {}
    for case in cases:
        response = service.triage(
            {
                "contractVersion": "0.1.0",
                "requestId": case["caseId"],
                "profile": "service-desk",
                "context": {"workspaceId": "ai07-offline"},
                "input": {"title": case["title"], "description": case["description"]},
            },
            trusted_context=TrustedContext(
                workspace_id="ai07-offline", subject="offline-control"
            ),
            profile_version="0.3.0",
        ).response
        canonical = json.dumps(
            response, ensure_ascii=True, sort_keys=True, separators=(",", ":")
        )
        results[case["caseId"]] = {
            "responseDigest": sha256(canonical.encode()).hexdigest(),
            "result": response["result"],
            "needsReview": response["needsReview"],
        }
    return results
