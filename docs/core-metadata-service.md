# Isolated Core Metadata capability

Branch: `feat/core-metadata-service`, based on `develop` at commit `347d2e5`
(merge PR #9 verifying File capability). Verified on 2026-10-15.
This implements the Data/Metadata capability according to the official brief's
section 4.1 (p3) and section 4.2 D - Data (p4).

## Architecture and Separation

- `MetadataRepository` encapsulates all SQL execution for metadata insertion,
  lookup, counting and dynamic allowlisted order-by queries.
- `JdbcMetadataService` implements `MetadataService`, enforcing:
  - Workspace membership for all operations.
  - Creation restricted to roles `agent` or `coordinator` within that workspace.
  - Exactly one of client-declared `checksum` OR `fileId`.
  - When `fileId` is supplied, `FileService` verifies file ownership and retrieves
    verified SHA-256 checksum before persisting metadata.
  - Upon metadata persistence, `FileService.link` connects file to the new metadata record.
  - Append-only transactional audit recording `metadata.create`.
  - Read access is workspace-shared among all members of that workspace.
- `MetadataFileAccessPolicy` implements `FileAccessPolicy` for `resourceType = "metadata"`.
  When a file is linked to a metadata record, any member of the workspace can read
  the file (`canRead`), and only agents/coordinators who own the metadata can link (`canLink`).
- `MetadataApi` handles HTTP translation, input validation, and delegates all
  business logic to `MetadataService`.
- Database Migration V4:
  - Adds compound unique constraint `(workspace, id)` on `file_object`.
  - Adds `file_id` column to `metadata`.
  - Adds composite foreign key `(workspace, file_id)` referencing `file_object(workspace, id)`,
    preventing cross-workspace file associations at the database layer.
  - Adds compound index on `metadata(workspace, created_at DESC, id DESC)` for fast paging.

## Review Findings Addressed

- **P1 JSON contract mismatch:**
  - `MetadataApi.View` previously serialized `fileId` without `@JsonProperty("file_id")`,
    causing a mismatch against OpenAPI `core.yaml` schema `Metadata`.
  - Superseded (2026-10-15, `fix/core-pre-hpdi`): the team chose camelCase for all Core
    responses. `View` now serializes `usageRights`, `fileId` and `createdAt` (ISO string).
  - Deserialization in `NewMetadata` supports both `fileId`/`file_id` and
    `usageRights`/`usage_rights` for backward compatibility.
  - `MetadataSpringIntegrationTest.mockMvcHttpResponseMatchesOpenApiMetadataSchemaExactly`
    verifies using `MockMvc` that the actual HTTP response JSON has exact fields
    (`id`, `workspace`, `title`, `source`, `version`, `usageRights`, `checksum`,
    `fileId`, `owner`, `createdAt`) matching OpenAPI `Metadata` schema.
- **P2 Spring transaction verification:**
  - Added `MetadataSpringIntegrationTest` creating an `AnnotationConfigApplicationContext`
    with `@EnableTransactionManagement`.
  - Confirmed `AopUtils.isAopProxy(metadataService)` is true.
  - Verified on real PostgreSQL 16.6 that an exception during `metadataService.create`
    (simulated audit failure) triggers automatic rollback via Spring's transactional proxy,
    leaving 0 metadata rows and 0 file_link rows.
  - Verified atomic commit when all operations succeed.
  - Verified DataSource and DataSourceTransactionManager consistency.
- **P2 Contract and Documentation:**
  - Corrected Vietnamese UTF-8 encoding in `contracts/openapi/core.yaml`.
  - Expressed the `exactly-one-of` requirement for `checksum` vs `fileId` via
    `oneOf: [ {required: [checksum]}, {required: [fileId]} ]` and schema description.
  - Synchronized PR #9 and PR #10 status tables and test counts.

## Reproducible verification

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-file.ps1
```

Verification uses PostgreSQL 16.6 and offline Maven test verify against clean databases.
**61 tests run, 0 failures, 0 errors, 0 skipped**:
- 10 Metadata service/controller tests (search, filter, paging, validation, cross-workspace denial, file-linking, audit rollback, concurrency).
- 4 Metadata Spring integration & HTTP contract tests (Spring AOP proxy rollback, commit, tx manager consistency, MockMvc schema verification).
- 1 V3->V4 upgrade and foreign key constraint regression test.
- 23 File storage regression tests.
- 12 Flowable workflow approval regression tests.
- 11 Identity/Audit/RequestId regression tests.

OpenAPI specifications (`contracts/openapi/core.yaml` and `contracts/openapi/core-target.yaml`)
were validated with `openapi-spec-validator` (0 syntax or reference errors).
