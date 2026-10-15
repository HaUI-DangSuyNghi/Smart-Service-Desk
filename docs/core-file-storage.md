# Isolated Core File capability

Branch: `feat/core-file-storage`, based on `develop` at workflow PR #8 merge
`ca2dc2a`, verified on 2026-10-15. This implements the agreed Data/File capability
under the official brief's p3 section 4.1; no particular object-storage product
is mandated by the exam. This is not a second web app or full Core acceptance.

## Service and security contract

`LocalFileService` implements the existing `FileService` port using JDBC, Java NIO
and a server-configured private directory. There is no new dependency or storage
framework. All methods require trusted `CurrentUser` workspace membership.
Runtime wiring must resolve that identity per request, not retain one user's
snapshot in a shared singleton. Fixtures in this branch are not authentication.

- Uploads accept at most 5 MiB, use generated UUID storage names, and compute SHA-256
  from the received bytes. The original filename is trimmed, bounded and validated
  against path separators, drive prefixes and header/control-character injection;
  it is metadata only, never a filesystem path. The caller owns the input stream.
- Allowed types are PDF, PNG, JPEG, UTF-8 plain text and Markdown. Extensions must
  match the type; binary signatures/end markers and strict text encoding/control
  checks reject obvious mismatches/truncation. These checks are not a complete
  document parser, malware scan, PDF active-content sanitizer or image decoder.
- An unlinked file is workspace-shared, per the existing FileService contract,
  not owner-private. Once linked, reads additionally require at least one matching
  `FileAccessPolicy.canRead` grant for the resource in that workspace. Unknown
  policies and missing/inaccessible resources deny reads with NOT_FOUND.
- Only the file owner may link a file they can currently read. A target policy
  must explicitly grant `canLink`, verifying target existence, workspace and
  attachment permission. Its new default implementation returns false, preserving
  existing implementors without accidentally granting attachment rights. Caller
  resource IDs alone never establish authorization. Links are idempotent.
- Storage roots and ancestor paths must be real directories, not symlinks. On
  POSIX filesystems the root must be 0700 and newly created objects are 0600.
  UUID paths use CREATE_NEW/NOFOLLOW_LINKS; missing, nonregular or symlinked content
  is not served. Existing permissive directories are refused, not silently chmodded.
- Downloads verify bounded size and SHA-256 before returning content. The physical
  file handle is closed before return, including transaction commit failures.

The configured directory must be owned by the service account; on non-POSIX
platforms deployment must enforce equivalent private ACLs. The tests run inside
Linux Docker, not a claim of native Windows ACL acceptance. A compromised service
account/root administrator is outside this filesystem authorization boundary.

## Persistence and failure behavior

V3 adds `file_object.original_name`; existing rows receive `download`, then the
default is removed so future writers must supply a name. V1/V2 are unchanged.
Migration preserves metadata, not nonexistent legacy file bytes. No production
database migration was executed during the isolated tests.

Metadata, links and `file.store`/`file.link` audit calls use one JDBC datasource and
transaction manager, checked at construction. File-row locks serialize linking
and authorization checks. Blob bytes are fully written/forced before metadata
commit. Transaction synchronizations remove new content on known rollback,
including failure in a caller's outer transaction. Link failure never removes an
already committed file. Reads do not grant a right to attach someone else's file.

A filesystem and database cannot provide one native distributed transaction.
Unknown DB commit outcomes retain content and log an error rather than delete a
possibly committed file. Cleanup failure is logged for manual reconciliation;
process crashes can leave private orphan blobs. Do not claim crash-atomic storage
or automatic recovery. Integration/operations must define reconciliation and
backup/restore before production exposure.

ponytail: objects are bounded at 5 MiB and buffered for validation/integrity; add
streaming verification and concurrency/memory controls if size or throughput grows.

## Reproducible verification

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-file.ps1
```

Prerequisites: Docker, cached `postgres:16.6-bookworm`, and the project's cached
Maven build image `dx-core-build:0.1.0`. Prepare that image on a clean machine with
`docker build --target build -t dx-core-build:0.1.0 backend`. The runner creates an
internal network and tmpfs PostgreSQL with separate File and Workflow regression
databases, no host ports and no shared data volumes. Maven runs offline clean
verify against current source; the runner removes only its own Docker resources.
Reports remain under ignored `backend/target/surefire-reports/`.

File tests use actual Flyway migrations/PostgreSQL/filesystem operations. Identities
and resource policies are fixtures; the test audit adapter writes real rows in the
same transaction but is not production JWT/Keycloak/AuditService wiring. The expected
ERROR log in the uncertain-commit test is an injected failure, not a passing claim
of automatic reconciliation. A regular Maven build without FILE_TEST_JDBC_URL skips
File integration tests and does not satisfy this feature's acceptance gate.

Verified on 2026-10-15, implementation commit `74c1e4e`: the final runner's offline
`mvn -B -o clean verify` passed **44 tests, zero failures/errors/skips**: 21 File
tests, 12 PostgreSQL/Flowable workflow tests and 11 existing regressions. JAR build
and Spring Boot repackaging passed. File coverage includes all allowed types,
5 MiB/oversize boundaries, SHA-256, Unicode/header/path controls, workspace/resource
denial, default-deny linking, idempotent concurrent links, audit/SQL/input/disk
failures, outer-transaction compensation, unknown commit retention, missing/changed
content, root/blob symlinks, private POSIX permissions, datasource mismatch, and
both fresh migration and V2-to-V3 upgrade preserving seeded legacy metadata.
`git diff --check` and a staged token/private-key heuristic passed; V1/V2,
workflow code and the existing metadata controller were checked unchanged.
These are isolated module/regression results, not HTTP/JWT/gateway/E2E evidence.

## Deferred integration

No Spring File bean, upload/download/link controller, HTTP multipart configuration,
gateway route, app policy adapter or metadata service refactor is enabled here.
The integration branch must wire verified identity/audit and real resource policies,
enforce request/body limits, safe attachment headers and nosniff, configure volume
ownership, and satisfy the architecture's content-scanning gate before exposing
uploads. Full JWT/gateway/E2E, fresh deployment, backup/restore and dependency/image
scans remain their documented acceptance gates. No release/main change is included.
