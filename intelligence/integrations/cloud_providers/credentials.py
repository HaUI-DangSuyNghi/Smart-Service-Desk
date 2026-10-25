import os
from pathlib import Path
import re

from .contracts import CloudFailure


class ServerCredential:
    """Read fresh on every attempt; secret values never enter identities/repr."""

    def __init__(self, vendor, environment=None):
        self.vendor = vendor
        self.environment = os.environ if environment is None else environment

    def read(self):
        prefix = self.vendor.upper() + "_API_KEY"
        value = self.environment.get(prefix, "")
        path = self.environment.get(prefix + "_FILE", "")

        try:
            if bool(value) == bool(path):
                raise ValueError()
            if path:
                file = Path(path)
                if (
                    file.is_symlink()
                    or not file.is_file()
                    or file.stat().st_size > 4096
                ):
                    raise ValueError()
                if os.name != "nt" and file.stat().st_mode & 0o077:
                    raise ValueError()
                value = file.read_text("utf-8").strip()
            if not re.fullmatch(r"[A-Za-z0-9_.-]{16,4096}", value):
                raise ValueError()
            return value
        except (ValueError, OSError, UnicodeError):
            raise CloudFailure("PROVIDER_CONFIGURATION_ERROR", "DISABLED", 0) from None
