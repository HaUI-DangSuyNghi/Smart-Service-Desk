"""One real synthetic bridge inference plus observed GPU placement and deployment isolation."""

import argparse

from _bootstrap import write_new
from local_runtime_support import deployment_evidence, gpu_identity, worker


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", choices=("tev1", "gemma3"), required=True)
    parser.add_argument("--container", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    deployment = deployment_evidence(args.container)
    identity = worker(args.container)
    result = worker(
        args.container,
        candidate=args.provider,
        fields={
            "title": "Tài khoản bị khóa",
            "description": "Không đăng nhập được trên một máy.",
        },
    )
    placement = result["placement"]

    if placement["context_length"] != 2048 or placement["size_vram"] <= 0:
        raise ValueError("Actual 2048-context GPU placement was not observed")

    write_new(
        args.output,
        {
            "contractVersion": "local-gpu-smoke@0.1.0",
            "scope": "SYNTHETIC_ARCHITECTURE_ONLY",
            "provider": args.provider,
            "runtimeDigest": identity["runtimeDigest"],
            "runtimeIdentity": identity,
            "deployment": deployment,
            "gpu": gpu_identity(),
            "placement": placement,
            "usage": result["usage"],
            "nativeElapsedMs": result["nativeElapsedMs"],
            "bridgeElapsedMs": result["elapsedMs"],
            "decision": result["observation"]["decision"],
            "operationalAuthority": False,
            "modelQuality": "NOT_EVALUATED",
            "productionApproved": False,
        },
    )
    print(
        "PASS real GPU bridge smoke; classification quality and release remain unapproved"
    )


if __name__ == "__main__":
    main()
