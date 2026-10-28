# History publication options - owner decision required

The committee form B8/C1 explicitly rejects a one-time source upload as complete
proof of substantive development. A fresh source candidate is a fallback/export
for review, NOT the default contest publication recommendation.

## Read-only evidence

Approved baseline ef047a31abfd8e35ce9b6155e3a06c224262269d has 369 reachable
commits dated 2026-10-06 through 2026-10-28, with real feature/test/doc changes and
merge subjects (including PR #62). All locally reachable refs: 99 refs, 406 commits,
9113 objects, 3512 blobs (30,220,589 bytes); four binary blobs were inventoried,
not text-secret-scanned. Exact refs/commit metadata are exported without rewriting.
No fetch, remote mutation, author-date editing, squash or rebase was performed.

Restricted assets found as historical blobs: official contest PDF, team plan PDF,
assignment XLSX and landing JPG. Reference files entered at 4c995de. Pending-rights
fixtures and inline/generated content also need scope review. Scan findings are
not a declaration of a credential breach: six strong-pattern findings occur in
negative security test inputs; assignment patterns also match environment-variable
references, generated test values and placeholder realm secrets. Full human
triage, PII review, server refs and attachments remain outside certification.

## Compare before authorizing any route

| Option | History strength | Safety/rights gates | Main contest risk | Recommendation |
| --- | --- | --- | --- | --- |
| A: publish current repository only after full clearance | Strongest: original creation date, commits, branches, tags and GitHub PR context preserved | Approve all reachable historical assets or obtain redistribution permissions; secrets/PII/attachments/refs reviewed and credentials rotated if real; formal source grant | Currently unsafe: pending references/data rights; making only a clean latest commit does not remove old blobs | Preferred ONLY if every historical gate can be cleared in time; do not change visibility now |
| B: separately filtered historical mirror, original untouched | Can preserve genuine author/committer dates, meaningful changes and topology; hashes change, empty commits may disappear | Owner-approved explicit path/blob policy; isolated mirror only; full before/after scan; old->new commit map and provenance; independent verification of no leaked refs/objects | New public repository creation date and broken original PR/hash links; removed commits need explanation; organizer must accept preserved history | Best candidate for preserving real development if A cannot clear, but requires explicit owner and organizer approval before any filtering |
| C: new public snapshot plus private provenance review | Public snapshot alone is weak; original private history/PR exports can support provenance only if committee accepts them | Sanitized source/data/notice gates still apply; independently review provenance exports for sensitive content | B8/C1 may be unrecognized despite good code/demo; cannot promise compliance | Last-resort fallback, not automatic recommendation; obtain explicit written committee acceptance |

Never fabricate/backdate commits, split one import into artificial development
commits, or rush cosmetic commits to simulate progress. Do not claim a filtered
mirror has original object IDs or public PR records. No route can be declared
compliant while LICENSE and rights approvals remain pending.

## Controlled mirror plan, NOT executed

1. Owner approves destination, exact exclusions/blob replacements and preservation
   requirements; organizer confirms acceptable provenance and contest dates.
2. Snapshot original refs/metadata privately; create a separate mirror from the
   approved object snapshot, never run filtering in original/worktree Git storage.
3. Review all reachable content, including historical copies/renames, fixture/data
   blobs, restricted photo, messages, notes, LFS and remote attachments. Path-only
   filtering may miss renamed assets. Define any blob-level redactions explicitly.
4. Use reviewed history-filter tooling in the mirror only. Preserve real dates and
   commit relationships wherever possible; record old->new SHA map and removed
   objects/empty commits. No timestamps or contribution claims are fabricated.
5. Scan every retained ref and object; ensure excluded bytes cannot be recovered
   from published refs, archive, LFS, releases or caches. Build/test final source.
6. Present comparison/map privately to owner and committee. Publish only approved
   sanitized refs to a new destination; never push a private mirror wholesale.
7. Keep original private repository and PR evidence intact. Any proposal to replace
   current repository history is a separate owner-approved operation, not this task.

If a real credential is discovered, revoke/rotate it first; history filtering
alone is not remediation. GitHub repository creation/PR metadata and contest
announcement dates still require authenticated owner/organizer verification.
