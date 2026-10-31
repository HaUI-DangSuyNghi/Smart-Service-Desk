from pathlib import Path
import runpy
from unittest.mock import Mock, patch

namespace = runpy.run_path(str(Path(__file__).with_name("triage-gateway.py")))
settled_ticket = namespace["settled_ticket"]
pending = {"assessment": {"status": "PENDING"}}
ready = {"assessment": {"status": "READY"}}
degraded = {"assessment": {"status": "DEGRADED"}}
assert settled_ticket(lambda: pending, background=False) == pending
assert settled_ticket(lambda: ready, background=True) == ready
assert settled_ticket(lambda: degraded, background=True) == degraded
read = Mock(side_effect=[pending, ready])
with patch("time.sleep"):
    assert settled_ticket(read, background=True) == ready
assert read.call_count == 2
try:
    settled_ticket(lambda: pending, background=True, timeout=0)
except AssertionError as error:
    assert "did not settle" in str(error)
else:
    raise AssertionError("Unfinished background processing must fail, not be ignored")
print("PASS triage settlement: ready, degraded, disabled workspace, delayed completion, timeout")
