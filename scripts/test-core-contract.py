import json
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
ticket_id = "00000000-0000-4000-8000-000000000001"
ticket = dict(id=ticket_id, workspace="lab-a", creator="requester", clientRequestId=ticket_id,
              title="Synthetic", description="Synthetic", createdAt="2026-10-17T00:00:00Z",
              process=dict(processInstanceId="process-1", processKey="service-desk",
                           businessKey=ticket_id, workspace="lab-a"))
stats = dict(workspace="lab-a", visibility="own", total=1, awaitingHandling=1,
             awaitingConfirmation=0, awaitingReview=0, completed=0, unclassified=0,
             assigned=1, inProgress=0, pending=0, reopened=0, closed=0, overdue=0,
             intake=0, following=0)
captures = [dict(method="GET", path="/workspaces/lab-a/tickets/" + suffix,
                 status=200, bodyJson=json.dumps(body))
            for suffix, body in (("statistics", stats), (ticket_id, ticket))]
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / "responses.json"
    command = [sys.executable, str(root / "scripts/check-core-contract.py"), str(path),
               str(root / "contracts/openapi/servicedesk.yaml")]
    path.write_text(json.dumps(captures), encoding="utf-8")
    result = subprocess.run(command, text=True, capture_output=True)
    assert result.returncode == 0, result.stderr
    del stats["completed"]
    captures[0]["bodyJson"] = json.dumps(stats)
    path.write_text(json.dumps(captures), encoding="utf-8")
    result = subprocess.run(command, text=True, capture_output=True)
    assert result.returncode != 0 and "required property" in result.stderr, result.stderr
print("PASS concrete statistics route beats ticket id template; templated detail and missing-field rejection")
