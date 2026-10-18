# Process contract

ProcessCapability starts app-owned requests and returns ProcessHandle/ProcessTimeline. WorkInbox carries processKey, businessKey, assignment and timestamps. ProcessContribution registers app policies; ProcessObservations provides authorized batch technical state without engine tables. Apps own state classifications.

Public Java API: `vn.haui.dx.hpdi.process.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.
