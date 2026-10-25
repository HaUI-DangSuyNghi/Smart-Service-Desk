"""Run the app-owned local cloud smoke launcher."""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _local_cloud_operator import main


if __name__ == "__main__":
    raise SystemExit(main())
