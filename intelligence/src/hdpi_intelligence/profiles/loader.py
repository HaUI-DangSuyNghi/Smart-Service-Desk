from pathlib import Path

from .models import RuntimeProfile, freeze
from .validation import load_documents


def load_profile(root: Path, manifest: str, identity: str) -> RuntimeProfile:
    profile, docs = load_documents(root, manifest, identity)
    return RuntimeProfile(
        identity=identity,
        profile_id=profile['profile']['id'],
        rules_version=profile['rules']['version'],
        normalization_version=profile['normalization']['version'],
        taxonomy=freeze(docs['taxonomy']),
        rules=freeze(docs['rules']),
        policy=freeze(docs['priorityPolicy']),
    )
