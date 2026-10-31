# Baseline dependency/license summary

Baseline ef047a31abfd8e35ce9b6155e3a06c224262269d; not latest-version advice.
Declared terms from existing inventories, not completed legal/compatibility audit.
Machine detail: dependency-inventory.csv (npm lock entries, Python resolution
metadata, direct Java declarations and container references). UNKNOWNs block
closure. No vendored dependency binaries or images are in the source archive.

Fresh Java resolution from the sanitized source: java-resolved-dependencies.txt;
java-license-metadata.csv lists 156 exact artifacts and inherited upstream POM
license declarations (zero UNKNOWN declarations); java-artifact-notices.csv records
162 embedded notice entries and hashes. This does not replace actual notice
texts when distributing compiled JARs or establish license compatibility.
The aggregate inventory has 601 npm lock entries, 73 Python evidence rows
(platforms can duplicate packages), 12 Java direct declarations and 33 container
references. Python tokenizers 0.21.0 has empty license metadata and notices in the
dense Windows report; the 0.23.3 supplement is NOT a substitute for that version.
Existing AI05 governance declares Apache-2.0, but exact 0.21.0 notice evidence
still needs supplementing/review before any binary distribution.

| Component | Exact baseline/version source | Declared upstream terms/evidence | Gap |
| --- | --- | --- | --- |
| Backend / Spring Boot | backend/pom.xml: 3.3.13, Java 17; project 0.1.0-SNAPSHOT | Apache-2.0; existing docs/legal/dependency-inventory.md | Current effective transitive/plugin notices |
| Flowable / networknt | 7.1.0 / 1.5.9 in POM | Apache-2.0 in existing inventory | Exact bundled notices |
| Jackson | 2.18.11 BOM in POM / existing resolved report | Upstream component review required | Confirm all modules and notices |
| Flyway / JDBC / test deps | Boot-managed; docs/security/release-readiness-20261026-dependencies.txt | Existing inventory says Apache-2.0 / BSD-2-Clause; JUnit EPL-2.0, Mockito MIT | Historical table versions are not current resolution |
| React / React DOM | 19.3.1 declarations; package-lock is resolved truth | Lock metadata in generated inventory | Verify artifact MIT text and closure |
| Vite / TypeScript | ^8.3.4 / ~6.0.3 declarations | Exact lock metadata; review MIT / Apache notices | Resolve and validate actual build |
| Fonts / icons | Geist ^5.3.0; Inter and Inter Tight 5.3.0; Tabler 3.49.0; Lucide ^1.59.0 | Exact lock metadata, font/icon upstream notices | OFL/font attribution and icon notice review |
| Python generic runtime | intelligence 0.3.0; CPython >=3.12,<3.13 | Source license pending | Do not apply base library terms to project |
| PyYAML / jsonschema | 6.0.2 / 4.23.0 | Existing AI02 wheel metadata/classifiers | Actual MIT and transitive notices review |
| scikit-learn / NumPy / SciPy | 1.6.1 / 2.2.6 / 1.15.3 | BSD-3-Clause plus bundled terms, AI03 governance | Numerical-library bundled notices |
| joblib / threadpoolctl | 1.4.2 / 3.6.0 | BSD-3-Clause per AI03 governance | Actual distribution notices |
| Optional ONNX / tokenizers | 1.20.1 / 0.21.0 in dense extra | MIT / Apache-2.0 per AI05 governance | Platform closure and model artifact notices |
| Qwen base / adapter | unsloth/Qwen3.5-0.8B revision 23c69c53358a07516b5827588b3fdb12ae78fd65 | Base Apache-2.0; licenses/models provenance | Adapter license NOT DECLARED; data rights pending; payload excluded |
| E5 model | intfloat/multilingual-e5-small rev 614241f622f53c4eeff9890bdc4f31cfecc418b3 | MIT declaration in ai05-dense-model.md | No weights included; separate notice review |
| PostgreSQL / Keycloak | compose: 16.15-bookworm / 26.0.8, exact digests retained | PostgreSQL / Apache-2.0; images mixed OS terms | Actual image package license inventory |
| APISIX / Temurin / Maven | Dockerfiles: 3.11.0-redhat / 17.0.20.1_1-jre-jammy / 3.9.9 | Apache-2.0 / OpenJDK GPL with Classpath exception / Apache-2.0 plus OS terms | Full image notices/source obligations |
| Node / nginx / Python images | 22.14.0-alpine / 1.27.4-alpine / 3.12.15-slim-bookworm | Mixed runtime/OS terms | Full image closure; Node/nginx tags not digest-pinned |
| Optional CUDA toolkit | 12.8.1 supplement evidence | NVIDIA EULA, not OSI license | Keep outside all-FOSS Core claim; no binary export |

Existing docs/legal/dependency-inventory.md contains older historical tables (Boot 3.3.4,
Keycloak 26.0.7 etc.). Use current manifests/resolution, not those historical pins.
Its security/... link omits docs/; original report actually lives in docs/security.
Evidence provenance is not source licensing or a production-security waiver.
