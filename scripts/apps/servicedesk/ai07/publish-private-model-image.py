"""Explicit private publication with independently signed distribution approval."""

import argparse
from pathlib import Path
from full_model_release import docker, inspect, read_json, require, verify_approval, verify_receipt, write_new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-receipt", type=Path, required=True)
    parser.add_argument("--approved-channel", required=True)
    parser.add_argument("--approval", type=Path, required=True)
    parser.add_argument("--approval-signature", type=Path, required=True)
    parser.add_argument("--approval-public-key", type=Path, required=True)
    parser.add_argument("--openssl", default="openssl")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--execute", action="store_true", help="Explicitly authorize private push; otherwise verify only")
    args = parser.parse_args()
    receipt = read_json(args.image_receipt)
    verify_receipt(receipt)
    approval = verify_approval(args.approval, args.approval_signature, args.approval_public_key, args.openssl)
    require(approval["modelDigest"] == receipt["modelDigest"] and approval["channel"] == args.approved_channel)
    channel = args.approved_channel
    require("/" in channel and "://" not in channel and "@" not in channel and ":" not in channel.split("/")[-1])
    require(not channel.startswith(("docker.io/", "index.docker.io/", "library/")), "Default public registry is not an approved channel")
    if not args.execute:
        print("PASS private publication preflight; push NOT_RUN")
        return
    tag = channel + ":" + receipt["modelDigest"][:16]
    docker("tag", receipt["distributionImageId"], tag)
    docker("push", tag)
    full = inspect(tag)
    digests = [ref for ref in full.get("RepoDigests", []) if ref.startswith(channel + "@sha256:")]
    require(len(digests) == 1 and full["Id"] == receipt["distributionImageId"])
    write_new(args.output, {**receipt, "distributionImageRef": digests[0], "distributionApproved": True,
                            "distributionApprovalIssuer": approval["issuer"]})
    print("PASS approved private OCI push; use the returned immutable distributionImageRef")


if __name__ == "__main__":
    main()
