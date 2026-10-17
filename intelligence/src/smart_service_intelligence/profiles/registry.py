from pathlib import Path
from threading import RLock

from ..domain.errors import ProfileNotFound, UnsupportedProfileVersion
from ..resources import resource_root
from .loader import load_profile
from .validation import confined, keys, read_yaml, require, verify_lock


class ProfileRegistry:
    """Exact immutable lookup. A new registry instance is needed for new config."""

    def __init__(self, root: Path | None = None):
        self._root = (root or resource_root('profiles')).resolve()
        registry = read_yaml(self._root / 'registry.yaml')
        keys(registry, 'registryVersion profiles')
        require(registry['registryVersion'] == '1', 'Unsupported registry version')
        self._cache = {}
        self._lock = RLock()
        self._entries = registry['profiles']
        require(isinstance(self._entries, dict) and self._entries, 'Missing registry entries')
        verify_lock(self._root)
        for identity, entry in self._entries.items():
            require(identity.count('@') == 1 and all(identity.split('@')), 'Malformed registry identity')
            keys(entry, 'file executable')
            require(type(entry['executable']) is bool, 'Invalid executable flag')
            confined(self._root, entry['file'])
            # Validate executable profiles at startup, not on the first ticket.
            if entry['executable']:
                self._cache[identity] = load_profile(self._root, entry['file'], identity)

    def get(self, profile_id: str, version: str):
        identity = f'{profile_id}@{version}'
        if not any(key.split('@')[0] == profile_id for key in self._entries):
            raise ProfileNotFound('Unknown profile')
        if identity not in self._entries or not self._entries[identity]['executable']:
            raise UnsupportedProfileVersion('Unknown or non-executable immutable profile version')
        with self._lock:
            return self._cache[identity]
