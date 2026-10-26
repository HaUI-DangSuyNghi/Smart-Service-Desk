# ADR-011: Model-primary category proposals with application rule guards

Status: implemented for engineering and opt-in assisted composition; independent
data, taxonomy, quality, licensing, security and release approvals remain pending.

The v1.6 model-primary handoff supersedes rule-primary classification as the
target for the new proposal flow. The existing `assistant/assess`, AI02 triage,
AI05 and AI06 contracts retain their historical behavior and immutable profiles.

The new `POST .../assistant/classify` endpoint obtains an authorized persisted
ticket, invokes a neutral classification capability with only bounded input
fields and pinned deployment identities, applies Service Desk guards, then
reauthorizes the actor and ticket and revalidates processing permission. The
model supplies the category proposal. A keyword cannot replace that category.
Missing urgency does not discard a category. Impact, urgency and priority remain
unknown in this category-only response; risk remains NOT_ASSESSED.

Rule types are SOFT_SIGNAL, HARD_EXCLUSION, REVIEW_CONFLICT, TAXONOMY_BOUNDARY and
SAFETY_GATE. Their literal input spans are verified by construction and expressed
in Unicode code-point offsets. They flag review; they do not establish semantic
entailment, root cause, a fallback OTHER, or permission to route a ticket.
The Python and Java policy copies have an enforced byte-equivalence test.

Core retains authorization, tenancy, persistence and resource authority. HDPI
exposes domain-neutral typed input/observation contracts and bounded inference.
Service Desk owns labels, guards, rollout mode, processing eligibility and human
review. Vendor/runtime code remains in integrations. No Service Desk import or
taxonomy is introduced into Core or HDPI, and the generic wheel excludes app
policies, raw datasets, model adapters and weights.

Modes are DISABLED (default), SHADOW and ASSISTED. SHADOW conceals the model vote
from the response. ASSISTED displays an explicitly unverified proposal with
needsReview=true, score=null, no automatic routing and operationalAuthority=false.
PROMOTED is refused until a separate reviewed model-selection and release design
can verify genuine approval. There is no hidden fallback model or rule category.

Even SHADOW requires an independently issued processing grant bound to workspace,
model digest, policy digest and expiry. A registered external issuer signs the
exact payload bytes using HMAC-SHA256; only the verification key and receipt path
are configured in the backend. No application endpoint or CLI creates approvals.
Synthetic test keys are not owner keys. This grant permits processing only; it
does not approve labels, supervised training, licensing, selection or release.
The receipt digest is checked again after inference so revocation or replacement
cannot release a stale proposal. Public callers cannot select models or provide
their own text, policy, score, priority, route or alleged approvals.

The HTTP capability permits one concurrent request, a four-second total deadline,
bounded request/response bodies, strict duplicate/trailing JSON rejection, pinned
model/policy/input/request identity and a short circuit after repeated failures.
It makes no automatic retry. Invalid/outage results become explicit degraded
abstention, while access, actor, ticket or grant drift denies the whole response.
Inference executes outside the surrounding database transaction. No ticket,
assignment, workflow or escalation mutation is performed.

LabOps v4 is a separate private engineering profile: machine-silver labels and
pending rights are preserved. Weak lexical/embedding experiments never construct
TrainingApproval and have no operational artifact loader or weight export. Their
role is NEVER_PROMOTABLE. Reviewed training requirements remain unchanged. A
separate authenticated DEV freeze can unlock Gold scoring, including all-case
coverage, rejected attempts and family-cluster uncertainty. Legacy holdout,
CASCADE and engineering challenge are not unseen final Gold; no new HOLDOUT or
authentic reviewer approval is manufactured by this implementation.

The existing private Ollama bridge stays separate from Java and accepts only its
server-pinned lab data scope. Runtime v0.2 binds ENGINEERING_SILVER to a specific
dataset manifest digest. The additive inference server is a separate authenticated
assembly; its shipped reference launcher is restricted to private engineering.
Real-data operation requires its own approved serving deployment, processing
grant and operational review. No network link, public model port or Core credentials
are added to the lab stack.

Rollback is DISABLED plus the existing historical rule API. Stop the isolated
AI07 Compose profile without deleting model volumes or immutable receipts. An
implementation commit and passing local tests do not certify hosted CI, an
accepted model champion or production readiness.
