# v0.1.0 dependency and provenance evidence

Generated after publication from frozen tag `v0.1.0` / commit
`ba82b2fc5eb36ea309563d2d7442db95b96a7608`. These are documentation supplements,
not new dependencies, new release assets or a replacement source release.
[Input SHA-256 values](source-inputs.json) pin the manifests used.

| Inventory | Scope and actual source |
| --- | --- |
| [Frontend CycloneDX SBOM](frontend-production.cdx.json) | Native npm 11.9.0 production graph: 119 components, package versions/purls/licenses and dependency edges from the released lockfile. |
| [npm lock inventory](npm-lock-inventory.csv) | All 602 non-root lock entries, including dev/optional packages, with declared license, resolved registry source and integrity where present. Bundled packages may not have their own registry URL/integrity. |
| [Java resolved inventory](java-resolved-inventory.csv) | Maven 3.9.9 / dependency-plugin 3.6.1 resolves 160 compile/runtime/test artifacts from the released POM. Declared licenses come from each cached upstream POM or its parent; exact source POM, Maven Central URL, actual cached JAR SHA-256 and license/notice entry count are recorded. |
| [Python baseline inventory](python-baseline-inventory.csv) | All 20 pins/markers in requirements.lock, including build/test tools. License metadata is the exact version's PyPI expression/classifiers, not a scan of all platform-specific wheels. |
| [Container references](container-source-references.csv) | 19 FROM/image source references, including optional profiles, with exact file/line and declared tag/digest. Local build tags and variable references are not registry artifacts or an OS package SBOM. |

## Reproduce without changing project dependencies

Use a fresh checkout of the released tag. Commands below read the existing
manifests and write only reports; use a temporary output directory.

```powershell
git clone https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk.git Smart-Service-Desk-pof
cd Smart-Service-Desk-pof
git switch --detach v0.1.0
npm --prefix frontend --version
Push-Location frontend
npm sbom --package-lock-only --omit=dev --sbom-format=cyclonedx | Out-File -Encoding utf8 "$env:TEMP/ssd-v010-frontend.cdx.json"
Pop-Location
mvn -B -f backend/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.6.1:list "-DoutputFile=$env:TEMP/ssd-v010-java-dependencies.txt" -Dsort=true
```

The recorded Maven run also used `-o` (warm cache) and
`-DoutputAbsoluteArtifactFilename=true` privately to hash resolved artifacts.
Absolute user/cache paths are NOT committed. Public CSV coordinates and hashes
can be checked against the recorded Maven Central POM/JAR sources. License
inheritance is recorded via `license_source_pom`; a semicolon-separated declared
list is NOT an assertion of an SPDX AND/OR license relationship.

For Python, every CSV row links `https://pypi.org/pypi/<name>/<version>/json`;
compare `info.license_expression` or `License ::` classifiers with the pinned
version/marker in requirements.lock. This is declared metadata, not an independent
binary-wheel license audit. For npm, compare each CSV lock_path directly to
`frontend/package-lock.json`; SBOM timestamps/serial numbers may change while
component purls/versions and dependency graph stay reproducible.

Native npm production SBOM generation PASS. An attempted full dev+optional SBOM
reported five missing optional WASI bundled-dependency metadata references from
Tailwind oxide. No lockfile/package update or weakened test was made to hide it.
The 602-entry CSV preserves all declared lock records, but is not a certification
of complete optional bundled subgraphs. Source build CI still passes; do not
present that separate metadata limitation as a successful full-stack SBOM.

## License compatibility boundaries and notices

Root Apache-2.0 applies to authorized original team-owned work. It does not
relicense dependencies or copied/generated upstream source. MIT/BSD/Apache
components retain their notice obligations. Java metadata also contains dual or
weak-copyleft declarations (for example Logback's EPL/LGPL choices); treat upstream
license alternatives/conditions individually, not as a blanket Apache grant.
OpenJDK's GPL/ClassPath Exception, PostgreSQL's license, fonts/icons, native
numerical-wheel subcomponents and container OS packages retain their own terms.

The released source archives do not bundle node_modules, Maven dependency JARs,
Python wheels, model weights or container image layers. A future binary/image
bundle needs its own full notice/license closure. Existing `licenses/**` and
upstream notices are unchanged. Image tags not digest-pinned remain a reproducibility
limit; this inventory does not silently freeze their future registry contents.
Optional profiles are separate, not newly certified default runtime:
[dense](../../../intelligence/requirements-dense.lock),
[Qwen Linux](../../../intelligence/requirements-qwen-linux.lock),
[Qwen Windows](../../../intelligence/requirements-qwen-windows.lock),
[model terms/provenance](../../../licenses/models/) and
[runtime supplements](../../../licenses/runtime-supplements/).

Run this short stdlib-only check from the repo root to verify recorded source inputs:

```powershell
py -3 -c "import hashlib,json,pathlib; root=pathlib.Path('.'); inputs=json.loads((root/'docs/publication/pof/source-inputs.json').read_text()); assert all(hashlib.sha256((root/path).read_bytes()).hexdigest()==digest for path,digest in inputs['source_sha256'].items()); print('PASS: released source-input hashes')"
```

[LICENSING](../../../LICENSING.md) now records PR #74's four asset-specific
confirmations. The [asset manifest](../release-distribution-status.csv) distinguishes
two direct author permissions from two reported HaUI permissions whose school
evidence still needs archiving. Publication is not independent rights clearance.
One MODERATE fast-uri 3.1.8 finding remains; this is not a clean security audit,
full image scan, or legal compatibility certificate.

## Final verification and portable source hashes

[Final verification](FINAL_VERIFICATION.md), [observations](final-verification.json)
and [sanitized log](final-verification.txt) record the actual source-build, smoke,
archive and provenance checks. `source-inputs.json` retains its original Windows
checkout byte hashes: eight text files use CRLF and differ from Git blobs. The
short checker above is a checkout-byte check, not a portable archive hash check.
The observations additionally contain canonical hashes under
`canonical_git_blob_hashes.source_sha256`, reproducible on any OS:

```powershell
py -3 -c "import hashlib,json,pathlib,subprocess; x=json.loads(pathlib.Path('docs/publication/pof/final-verification.json').read_text())['canonical_git_blob_hashes']; assert all(hashlib.sha256(subprocess.check_output(['git','show',x['release_sha']+':'+p])).hexdigest()==h for p,h in x['source_sha256'].items()); print('PASS: canonical released Git blob hashes')"
```

The 602-entry lock inventory has 458 dev and 144 production-or-peer records; its
51 optional records are all within dev. These flags do not imply 144 installed
production SBOM components (119 were reproduced), nor complete optional bundled
subgraphs. No dependency, lockfile or notice has been changed by this verification.
