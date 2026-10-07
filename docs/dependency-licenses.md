# Dependency, provenance and license inventory

Reviewed 2026-10-07. Versions are compatibility pins, not claims of latest releases or absence of CVEs. Do not publish this development stack as a production service. Project source license is pending team approval: no blanket license is applied to supplied PDFs/XLSX or other third-party material.

| Component | Pinned version / origin | Upstream license | Role |
| --- | --- | --- | --- |
| Spring Boot | 3.3.4; `org.springframework.boot:spring-boot-starter-parent` from Maven Central | Apache-2.0 | Web, validation, resource-server, actuator, JDBC dependency management |
| Flowable OSS | 7.1.0; `org.flowable:flowable-spring-boot-starter-process` from Maven Central | Apache-2.0 | Embedded BPMN runtime/history |
| Keycloak | `quay.io/keycloak/keycloak:26.0.7` | Apache-2.0; image includes additional OS packages | Identity provider |
| Apache APISIX | `apache/apisix:3.11.0-debian` | Apache-2.0; bundled dependencies have their own notices | Controlled API routing |
| PostgreSQL | `postgres:16.6-bookworm` | PostgreSQL License; image includes Debian packages | Core and separate IdP databases |
| Eclipse Temurin runtime | `eclipse-temurin:17.0.12_7-jre-jammy` | OpenJDK GPL-2.0 with Classpath Exception; OS packages separately licensed | Java runtime, separate process distribution |
| Maven build image | `maven:3.9.9-eclipse-temurin-17` | Maven Apache-2.0; JDK/OS retain own terms | Source build and tests |
| Flyway core + PostgreSQL adapter | 10.10.0 via exact Boot 3.3.4 BOM | Apache-2.0 for these community artifacts | Core-owned SQL migrations |
| PostgreSQL JDBC | 42.7.4 via exact Boot BOM | BSD-2-Clause | JDBC |
| JUnit / Mockito / Spring test | 5.10.3 / 5.11.0 / 6.1.13 via exact Boot BOM, test-scoped | EPL-2.0 / MIT / Apache-2.0 respectively | Tests, not app functionality |
| PowerShell scripts | Platform tool, not bundled | Platform distribution terms | Local setup/probes |

Primary sources checked: tagged upstream license files for Flowable 7.1.0, APISIX 3.11.0, Keycloak 26.0.7 and Spring Boot v3.3.4, and PostgreSQL's official license page. These top-level checks do NOT constitute a complete transitive or container-image legal/security audit.

Source references:

- https://github.com/flowable/flowable-engine/tree/flowable-7.1.0 (root POM explicitly pins Spring Boot 3.3.4; LICENSE)
- https://github.com/spring-projects/spring-boot/tree/v3.3.4 (LICENSE.txt and dependency BOM)
- https://github.com/keycloak/keycloak/tree/26.0.7 (LICENSE.txt)
- https://github.com/apache/apisix/tree/3.11.0 (LICENSE and standalone configuration)
- https://www.postgresql.org/about/licence/
- https://github.com/pgjdbc/pgjdbc
- https://github.com/flyway/flyway
- https://adoptium.net/about/
- https://maven.apache.org/ref/3.9.9/

No commercial Flowable distribution or proprietary Identity/Gateway component is used. The dependency set is based on openly licensed components; redistribution still requires preserving upstream notices and auditing bundled transitive/image packages. Do not infer legal compatibility solely from this short table.

## Release gates

Full resolved Java dependency versions are captured in `docs/dependency-tree.txt`, generated from the actual build image with `mvn dependency:tree`. Top-level version/license checks are not a full transitive audit.

Observed image digests (2026-10-07, Docker Desktop Linux):

| Image | Digest |
| --- | --- |
| PostgreSQL 16.6-bookworm | `sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36` |
| Keycloak 26.0.7 | `sha256:4388e2379b7e870a447adbe7b80bd61f5fbf04e925832b19669fda4957f05a81` |
| APISIX 3.11.0-debian | `sha256:178d1f79c2c39834f50213bf6ace90284f6b985dc8189cc50f3666d9fb1037ad` |
| Maven 3.9.9-eclipse-temurin-17 | `sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc` |
| Temurin 17.0.12_7-jre-jammy | `sha256:6c4c0938462c9678fe380bf01c7da7f4242dc20f54362f24ced3bb70f6e0183a` |

1. Team chooses and approves a source license; record authorship and exclude supplied third-party documents as necessary. Until then this repo is not a completed Proof of FOSS submission.
2. FE captures image digests and installed package inventory; BE captures `mvn dependency:tree` / effective POM; AI checks sample-data rights. Maven BOM versions are deterministic but image tags can move; record digests for release.
3. Scan dependencies/images, remediate findings and upgrade to supported patch versions with full integration tests. The chosen 2024 compatibility baseline is old as of October 2026. Security currency remains unverified and blocks public deployment.
4. Preserve LICENSE/NOTICE files of redistributed components and image packages. Check transitive licenses, not only names above. Generate SBOM before release; no clean security bill is claimed here.
5. Verify clean source build, backup/restore, offline runtime after preload and actual release tag. No automatic publishing is authorized.
