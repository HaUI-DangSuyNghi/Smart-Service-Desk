# Final PoF verification - 2026-10-31

This is post-release evidence, not Council scoring, a new release or a security/legal
certificate. Frozen source: `ba82b2fc5eb36ea309563d2d7442db95b96a7608`.
See [machine-readable observations](final-verification.json) and
[sanitized command/output transcript](final-verification.txt). Full local build logs,
generated secrets and synthetic response bodies remain outside Git; the public log
contains selected build output and complete redacted smoke output, not full raw logs.

## Six criteria: direct evidence and actual gaps

| Criterion | Verified evidence | Remaining gap / limit |
| --- | --- | --- |
| Git history | 401 reachable commits and 68 reachable merged PRs: BE/integration MinhHieuDev2005 32; AI ManhT005 24; FE HuyHoang3425 12. Commit/PR timeline below. | Official contest opening/closing dates: **CHUA XAC MINH / CHƯA XÁC MINH**. Git timestamps are author-controlled and counts do not prove effort or compliance with a contest window. |
| Licensing | Root LICENSE text matches the official [Apache text](https://www.apache.org/licenses/LICENSE-2.0.txt), allowing trailing/line-ending normalization. [PR #74](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/74) merged; [LICENSING](../../../LICENSING.md) and [82-asset manifest](../release-distribution-status.csv) retain all asset hashes. Existing `licenses/**` unchanged. | Two HaUI permissions are reported by the leader, not independently verified; archive school permission and conditions. Two DX-Lab documents have direct author distribution permission, not a new Apache grant. No complete legal compatibility/notice-closure certification. |
| Release | [v0.1.0](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/releases/tag/v0.1.0) published 2026-10-31T05:31:12Z; lightweight tag at frozen SHA. Fresh ZIP and tar.gz each match 1,704 exported Git files. | Archive bytes include the external assets. `git archive` honors attributes (`dx.cmd` exports CRLF). Download hashes identify these downloads, not an eternal GitHub compression guarantee. No tag movement. |
| Source build/install | Fresh tag clone; clean npm install and FE build PASS; Java 17/Maven clean source package PASS (307 production sources compiled; tests skipped explicitly). Artifacts deployed outside source; real token login, API, ticket/workflow/RBAC and recovery smoke PASS under the disclosed isolated override. | Released PostgreSQL image failed startup in this harness. Unmodified full Compose, APISIX path, browser login and Python/AI clean install are NOT TESTED in this run. This is NOT a default-stack clean-build PASS. |
| Dependencies/provenance | 602 npm records matched to lock; 119 production SBOM purls/versions reproduced; 160 cached Java JAR hashes and license-source POM declarations matched; 20 Python pins and upstream declared license metadata matched; 19 container source references matched. | 51 optional npm records (all dev) are not full bundled optional SBOM closure. Cached Java matches are not independent Central re-downloads. Wheel/native/OS contents and all binary notice obligations are not certified. One recorded MODERATE fast-uri 3.1.8 remains; no fresh security audit/waiver. |
| Docs/issues | README, CHANGELOG, CONTRIBUTING, [Core OpenAPI](../../../contracts/openapi/core.yaml), [Service Desk OpenAPI](../../../contracts/openapi/servicedesk.yaml), [architecture](../../architecture/tier-boundary-map.md) and evidence index checked for relevant paths/claims; stale four-permission statements corrected. | [Issue #16](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/issues/16) remains open with zero comments at observation time. Its body reports earlier local tests, not a recorded final reviewer acceptance. No issue closed or fabricated. |

## Short real timeline (UTC)

- 2026-10-06T15:09:22Z: [first commit](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/11773eced0b07ddee6230038f905e5cae56c5250); commit author/committer time, not external contest proof.
- 2026-10-15T09:36:19Z: BE workflow [PR #8](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/8), [merge commit](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/ca2dc2ac8ae87adcc04cac87099d0bc3bb86395e).
- 2026-10-17T09:20:45Z: AI runtime [PR #15](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/15), [merge commit](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/6f312eb21f2f71d89388c84c143f963949faffcc).
- 2026-10-19T16:52:40Z: FE base [PR #29](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/29), [merge commit](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/2b74f5437efc11ca40040ef1252d2b7122a65431).
- 2026-10-23T05:46:30Z: grounded assistance [PR #35](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/35).
- 2026-10-28T17:29:43Z: FE ticket workflow [PR #63](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/63).
- 2026-10-31T05:25:07Z: final submission [PR #72](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/72) merged; release published at 05:31:12Z.

Git reachability and GitHub merge timestamps were checked separately. The supplied
[HaUI exam PDF](../../references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf) cites a
VFOSSA article dated 2026-09-30, but does not establish the official contest window
in this verification. External lookup did not establish an independently verifiable
opening/closing notice. The 2026-11-01 submission deadline remains team-supplied.
Provide the organizer's dated announcement/URL before asserting window compliance.

## Exact build and deployment boundary

Only new private directories were used: one tag clone and one separate evidence/
deployment directory. No application build output was borrowed from the demo or an
existing app image. npm/Maven download caches were warm; this is not an empty-cache
or offline reproducibility claim. Tool versions and artifact hashes are in JSON.

Commands executed (directory names are placeholders for those new directories):

```powershell
git clone --branch v0.1.0 --single-branch https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk.git <fresh-source>
cd <fresh-source>
git rev-parse HEAD
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
$env:VITE_OIDC_AUTHORITY='http://localhost:38081/realms/dx-lab'
$env:VITE_OIDC_CLIENT_ID='dx-web'
npm.cmd --prefix frontend ci --no-audit --no-fund
npm.cmd --prefix frontend run build
docker run --rm --network bridge --mount type=bind,source=<fresh-source>,target=/source --mount type=bind,source=<maven-cache>,target=/root/.m2/repository -w /source/backend maven:3.9.9-eclipse-temurin-17 mvn -B -DskipTests clean package
```

The initial frontend build was repeated with the final `dx-web`/localhost authority
above before copying output. Setup generated secrets before deployment; frontend
public OIDC configuration was set before its final build. Generated secrets/realm
and private response bodies are never attached to this PR.

Only `backend/target/*.jar`, `frontend/dist`, the released PostgreSQL init SQL and
the generated realm/config were copied into the separate deployment directory.
The isolated Compose harness takes the released postgres/keycloak/platform service
configuration; it replaces app image/build with the newly built JAR mounted read-only
into the same digest-pinned Maven/Java 17 tool image. The JAR runs from `/run-proof`,
not `/source`. Dedicated DB/files volumes and loopback ports 38081/38080 are used.
The wrapper sets owner-only mode 700 on its own new file-storage volume, matching
the released Dockerfile; an initial wrapper attempt without that permission failed
and was corrected in the test configuration only. No assertions were weakened.

The first isolated project used the released PostgreSQL reference
`postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4`.
It exited 1: `find: 'postgres' is not the name of a known user`. This is an observed
image startup failure on this host, not proof of its root cause or universal failure.
A DIFFERENT fresh proof project used explicit test-only PostgreSQL 16.6 digest
`sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36`.
Keycloak retained its released 26.0.8 digest. No image/lockfile/source was patched.
The fallback deployment reached healthy, then exercised the source-built JAR.

```powershell
# From the separate deployment directory, never from the demo:
docker compose -p ssd-pof-v010-proof-20261031-fallback -f compose-fallback.yaml up -d --wait --wait-timeout 240
py -3.11 -m http.server 35173 --bind 127.0.0.1 --directory ./web
# Smoke harness reads only this private deployment .env, never prints tokens.
py -3.11 ../smoke-local.py ../smoke-responses.private.json
docker compose -p ssd-pof-v010-proof-20261031-fallback -f compose-fallback.yaml restart platform
# After health returned 200:
py -3.11 ../smoke-recovery.py ../recovery-responses.private.json ../smoke-responses.private.json
docker compose -p ssd-pof-v010-proof-20261031-fallback -f compose-fallback.yaml stop
```

The smoke harness is the frozen [versioned Service Desk acceptance script](../../../scripts/acceptance/servicedesk.py).
Only token/API URL ports (8081 to 38081; 9080 to direct backend 38080), explicit
UTF-8-sig `.env` reading, and the truthful direct-backend success banner differ.
Recovery uses 127.0.0.1 for those HTTP connections; configured token issuer stays
localhost:38081. All original assertions were retained and their equality checked.
Five synthetic users authenticate; positive/negative authorization, ticket creation,
review/claim/handle/rework/confirmation, audit and duplicate concurrency are exercised.
After restarting only the new proof backend, three tickets retained content,
ownership, workflow references and readable engine progress.

Frontend static HTTP returned 200 outside source. This is not browser OIDC E2E:
the static server has no API reverse proxy. The isolated realm allows only the new
35173 origin for the `dx-web` client; no existing realm was reset. APISIX, browser
login, AI/native Python install and an unmodified full default stack were NOT TESTED
here. Host Python 3.11.6 is only the stdlib test driver, not evidence for the project's
Python 3.12 runtime installation. The original pinned-Postgres failure is NOT waived
by the fallback smoke PASS. All proof services were stopped; proof volumes retained.
Previously running container IDs remained running; tracked source stayed unchanged.

## Existing CI, source hashes and provenance limits

[Run 41172037297](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/41172037297)
was re-queried: seven successful jobs on `0835c86375c1b2056bb59018294eeebc4ac2a16c`.
Its Git tree equals the frozen release. This remains historical equivalent-source
CI evidence, not a new run on this documentation commit. Earlier 21-check evidence
is retained separately; no full regression suite was rerun for this documentation.

The old `source-inputs.json` hashes describe a Windows checkout: eight text files
have CRLF hashes differing from Git blob hashes. All 15 match the new Windows clone.
Do not present those as portable archive hashes. This verification additionally
records canonical `git show <release-sha>:<path>` SHA-256 values in the JSON report.
No prior hashes, payloads or lockfiles were silently changed.

npm: 458 dev records, 144 production-or-peer lock records (not 144 installed prod
components); 51 optional records are included within dev. Reproduced production SBOM
has 119 purl/version components. Full dev/optional bundled-subgraph SBOM remains
incomplete as previously recorded. Java: 126 compile, four runtime, 30 test records;
licenses are upstream POM declarations, not independent legal opinions. Python:
20 exact pins/markers, with upstream metadata matched for those versions; wheel
contents/transitive native notices were not scanned. Container references are build/
optional/source references, not an OS SBOM, and tag-only references are mutable.
Preserve all upstream license/notice files. No dependency updates or fresh audit;
the recorded MODERATE fast-uri finding remains, without a waiver.

## Owner evidence still to archive

- Organizer's official competition start/end/deadline notice (URL or dated document).
- Dated leader statement covering the four exact assets from PR #74; retain author
  identity/permission for the DX-Lab PDF/XLSX without inventing an Apache grant.
- HaUI written/email/link confirmation identifying the exam PDF and room photo,
  public-use/posting scope, school approver and any conditions/required attribution.
  The leader's report is recorded; no separate school permission was supplied here.
- Actual reviewer acceptance linked to issue #16 before claiming closure. This run's
  direct-backend smoke does not alone prove all gateway/recovery acceptance criteria.
- A separately approved diagnosis/retest for the pinned PostgreSQL startup failure
  before claiming the unmodified default install passes. No tag or source fix is made.
