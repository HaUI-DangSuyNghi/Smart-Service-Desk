"""Verify immutable wrapper ancestry and all embedded model bytes without network/mounts."""

import argparse
from pathlib import Path
from full_model_release import read_json, verify_receipt, write_new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    receipt = read_json(args.image_receipt)
    verify_receipt(receipt)
    write_new(args.output, {"schemaVersion": "ai07-full-model-verification@1.0.0", "integrity": "PASS",
                            "distributionImageId": receipt["distributionImageId"], "modelDigest": receipt["modelDigest"],
                            "network": "none", "hostModelMounts": False, "inference": "NOT_RUN", "productionApproved": False})
    print("PASS offline image ancestry and embedded bundle; inference NOT_RUN")


if __name__ == "__main__":
    main()
