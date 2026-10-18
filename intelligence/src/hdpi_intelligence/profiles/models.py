from dataclasses import dataclass
from collections.abc import Mapping
from types import MappingProxyType
from typing import Any


def freeze(value):
    if isinstance(value, dict):
        return MappingProxyType({key: freeze(item) for key, item in value.items()})
    if isinstance(value, list):
        return tuple(freeze(item) for item in value)
    return value


@dataclass(frozen=True)
class RuntimeProfile:
    identity: str
    profile_id: str
    rules_version: str
    normalization_version: str
    taxonomy: Mapping[str, Any]
    rules: Mapping[str, Any]
    policy: Mapping[str, Any]
