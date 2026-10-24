from dataclasses import asdict
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import re

from hdpi.generation.policy import GenerationPolicy

from .contracts import DATA_CLASS, Route, decode_json, require


def text(value, maximum=200):
    require(type(value) is str and bool(value.strip()) and len(value) <= maximum)


def checksums(values):
    require(type(values) is list and 1 <= len(values) <= 256)
    require(
        all(
            type(item) is str and re.fullmatch(r"[a-f0-9]{64}", item) for item in values
        )
    )
    require(len(set(values)) == len(values))


def pinned_file(path, expected, *, outside=None):
    try:
        file = Path(path).resolve(strict=True)
        require(not Path(path).is_symlink())
        if outside is not None:
            require(not file.is_relative_to(outside.resolve()))
        require(file.is_file() and 0 < file.stat().st_size <= 131072)
        raw = file.read_bytes()
        require(0 < len(raw) <= 131072)
        require(type(expected) is str and re.fullmatch(r"[a-f0-9]{64}", expected))
        require(sha256(raw).hexdigest() == expected)
        return decode_json(raw), file
    except (OSError, ValueError, TypeError):
        require(False)


class ProviderRegistry:
    def __init__(
        self,
        catalog_path,
        catalog_digest,
        approval_path,
        approval_digest,
        isolation_path,
        environment,
        *,
        repository_root,
        clock=None
    ):
        require(environment == "isolated-synthetic")
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.catalog, self.catalog_path = pinned_file(catalog_path, catalog_digest)
        self.approval, self.approval_path = pinned_file(
            approval_path, approval_digest, outside=repository_root
        )
        self.catalog_digest = catalog_digest
        self.approval_digest = approval_digest
        try:
            require(
                Path(isolation_path).is_file()
                and 0 < Path(isolation_path).stat().st_size <= 131072
            )
            isolation_raw = Path(isolation_path).read_bytes()
            isolation_digest = sha256(isolation_raw).hexdigest()
            isolation, self.isolation_path = pinned_file(
                isolation_path, isolation_digest, outside=repository_root
            )
            self.isolation_digest = isolation_digest

            c, a = self.catalog, self.approval
            require(
                set(c)
                == {
                    "apiVersion",
                    "mode",
                    "policyId",
                    "profileId",
                    "profileVersion",
                    "policy",
                    "allowPaidUsage",
                    "maxAttempts",
                    "routes",
                }
            )
            require(
                c["apiVersion"] == "cloud-selection@0.2.0"
                and c["mode"] == "synthetic-cloud-dev"
            )
            require(
                c["allowPaidUsage"] is False
                and type(c["maxAttempts"]) is int
                and c["maxAttempts"] == 1
            )
            for key in ("policyId", "profileId", "profileVersion"):
                text(c[key], 100)
            self.policy = GenerationPolicy(**c["policy"])
            require(self.policy.max_claims <= 3 and self.policy.needs_review is True)

            require(
                set(a)
                == {
                    "schemaVersion",
                    "status",
                    "owner",
                    "environmentId",
                    "expiresAt",
                    "catalogDigest",
                    "dataClass",
                    "questionDigests",
                    "sourceDigests",
                    "actors",
                    "resources",
                }
            )
            require(
                a["schemaVersion"] == "cloud-egress@0.2.0" and a["status"] == "APPROVED"
            )
            require(
                a["catalogDigest"] == catalog_digest and a["dataClass"] == DATA_CLASS
            )
            text(a["owner"])
            text(a["environmentId"])
            checksums(a["questionDigests"])
            checksums(a["sourceDigests"])
            require(type(a["actors"]) is list and 1 <= len(a["actors"]) <= 100)
            require(type(a["resources"]) is list and 1 <= len(a["resources"]) <= 100)
            for actor in a["actors"]:
                text(actor, 100)
            for resource in a["resources"]:
                require(set(resource) == {"workspace", "id", "contentDigest"})
                text(resource["workspace"], 100)
                text(resource["id"], 36)
                checksums([resource["contentDigest"]])
            require(
                set(isolation)
                == {
                    "schemaVersion",
                    "environmentId",
                    "catalogDigest",
                    "databasePurpose",
                    "production",
                }
            )
            require(
                isolation
                == {
                    "schemaVersion": "isolated-synthetic@0.1.0",
                    "environmentId": a["environmentId"],
                    "catalogDigest": catalog_digest,
                    "databasePurpose": "synthetic-only",
                    "production": False,
                }
            )

            require(type(c["routes"]) is list and 1 <= len(c["routes"]) <= 2)
            self.routes = {}
            vendors = set()
            for item in c["routes"]:
                require(
                    set(item)
                    == {
                        "id",
                        "vendor",
                        "model",
                        "observedModels",
                        "priority",
                        "maxInputBytes",
                        "maxOutputTokens",
                        "timeoutSeconds",
                        "rpm",
                        "rpd",
                        "tpm",
                        "tpd",
                    }
                )
                require(
                    type(item["id"]) is str
                    and re.fullmatch(r"[a-z][a-z0-9-]{0,63}", item["id"])
                )
                require(
                    item["id"] not in self.routes
                    and item["vendor"] in {"gemini", "groq"}
                )
                require(item["vendor"] not in vendors)
                vendors.add(item["vendor"])
                require(
                    type(item["model"]) is str
                    and re.fullmatch(
                        r"[A-Za-z0-9][A-Za-z0-9._/-]{0,160}", item["model"]
                    )
                )
                require(not item["model"].startswith("REQUIRED"))
                require(
                    type(item["observedModels"]) is list
                    and 1 <= len(item["observedModels"]) <= 10
                )
                for observed in item["observedModels"]:
                    text(observed)
                    require(not observed.startswith("REQUIRED"))
                limits = {
                    "priority": (0, 1000),
                    "maxInputBytes": (256, 64000),
                    "maxOutputTokens": (32, 1024),
                    "timeoutSeconds": (1, 12),
                    "rpm": (1, 10000),
                    "rpd": (1, 100000),
                    "tpm": (256, 100000000),
                    "tpd": (256, 1000000000),
                }
                for key, (lower, upper) in limits.items():
                    require(type(item[key]) is int and lower <= item[key] <= upper)
                route = Route(
                    item["id"],
                    item["vendor"],
                    item["model"],
                    tuple(item["observedModels"]),
                    item["priority"],
                    item["maxInputBytes"],
                    item["maxOutputTokens"],
                    item["timeoutSeconds"],
                    item["rpm"],
                    item["rpd"],
                    item["tpm"],
                    item["tpd"],
                    catalog_digest,
                    self.policy.identity,
                )
                require(
                    route.tpm >= route.max_input_bytes + route.max_output_tokens + 1024
                )
                require(
                    route.tpd >= route.max_input_bytes + route.max_output_tokens + 1024
                )
                self.routes[route.id] = route
            self.validate_current()
        except (KeyError, TypeError, ValueError):
            require(False)

    def validate_current(self):
        pinned_file(self.catalog_path, self.catalog_digest)
        pinned_file(self.approval_path, self.approval_digest)
        pinned_file(self.isolation_path, self.isolation_digest)
        try:
            expiry = datetime.fromisoformat(
                self.approval["expiresAt"].replace("Z", "+00:00")
            )
            require(expiry.tzinfo is not None and self.clock() < expiry)
        except (TypeError, ValueError):
            require(False)

    def bind(self, payload):
        self.validate_current()
        binding = payload["routeBinding"]
        require(
            set(binding)
            == {
                "routeId",
                "policyId",
                "dataClass",
                "catalogDigest",
                "routeDigest",
                "approvalDigest",
                "attemptId",
                "expectedRuntime",
            }
        )
        route = self.routes.get(binding["routeId"])
        require(route is not None)
        require(
            binding["catalogDigest"] == self.catalog_digest
            and binding["routeDigest"] == route.digest
        )
        require(
            binding["policyId"] == self.catalog["policyId"]
            and binding["dataClass"] == DATA_CLASS
        )
        require(
            binding["approvalDigest"] == self.approval_digest
            and binding["expectedRuntime"] == asdict(route.runtime)
        )
        require(
            sha256(payload["question"].encode()).hexdigest()
            in self.approval["questionDigests"]
        )
        require(
            all(
                item["checksum"] in self.approval["sourceDigests"]
                for item in payload["documents"]
            )
        )
        return route
