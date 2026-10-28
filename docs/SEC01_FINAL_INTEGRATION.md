# SEC-01 final integration - 2026-10-18 (Asia/Saigon)

## A. Retarget
PR #23 da retarget thanh cong sang develop qua GitHub API; van OPEN/Draft.
Khong merge, release, force-push, reset, rewrite history hay thay visibility.
Khong sua worktree FE/AI hay untracked documents cua worktree goc.

## B. Source identity
- Develop: `728cdc3d1b8847b6fd70dca210fa7a8455293e3e`.
- SEC-01: `43df9d745cbfb190cdfca8ac9c08f080c0851b36`.
- Merge-base: `0e034bf936595661fc71e55e31510c019c84f695`.
- Combined tree: `e78312036d66c54ed5612e8a3659bffb4ccbf3dc`.
- Worktree: `D:/Smart-Service-Desk-sec01-final`.

API va develop history xac nhan #22/#24/#25 da merged. Three-dot diff gom
16 files security pins, scan/test consumers va evidence; seven security commits
duoc bao toan. Merge-tree va merge --no-commit --no-ff khong conflict.
Khong tao commit moi. Build label la tree-<combined tree>; day la staged merge,
KHONG phai clean committed checkout. Khong co BE-02/business refactor.
Merge ref GitHub sau retarget van la `5bc5e5a180d6b63ebe0a40eacf78f8c17f334c16`
voi parent dau la base cu, khong phai develop. Khong dung ref nay cho combined CI.
Check-runs/actions queries khong tra ve run cho documentation HEAD; khong ke
thua CI cua runtime SHA cu thanh ket qua combined-source.

## C. Combined regression
Logs va XML nam tai `.local/sec01-final/`.
Java 17 + pinned PostgreSQL: 91 tests, 0 failures, 30 errors, 0 skipped.
Database connection refused; KHONG PASS. Pinned PostgreSQL thoat voi
`find: 'postgres' is not the name of a known user`; probe rieng bao thieu
user postgres va `libldap-2.5.so.0: file too short`. Pull theo amd64 child
manifest `sha256:ec9f656f17c8b4dfebf49e58cf5c46d493d4846d4ea6d8b85d0637f68ee33bfc`
lap lai cung loi. Chua ket luan upstream hay local Docker content-store fault.
Khong downgrade image hay sua user/library/volume de che loi.

Python lan dau 264 pass, 1 fail vi runner thieu git. Retest dung Git va
read-only Git common directory: source 265 pass, installed wheel 265 pass;
pip check va built-wheel verification PASS. Day la regression/unit evidence,
khong thay real integration (unit suite co test doubles).
Keycloak binary version probe chi la diagnostic, KHONG la login/JWT evidence.
No-cache build Core, gateway va Intelligence tu combined tree da hoan tat.
Full Compose startup FAIL tai PostgreSQL; Core/gateway/Keycloak chi o Created.
Real built Intelligence HTTP probe PASS: 401 khi khong co credential, 200 va
SUCCESS voi valid service credential va synthetic request. Day KHONG phai
Java-Python, Keycloak hay gateway integration. Da stop chi Intelligence container
cua project nay sau probe; khong xoa volume, image hay evidence.
Keycloak login/JWT, APISIX, Workflow, Ticket/Statistics, Java-Python, fallback,
workspace isolation, PKCE va browser UI chua duoc chung nhan tren stack nay;
database startup chan integration. Khong dung mock/healthcheck de thay the.

## D. Fresh vulnerability evidence
Khong overwrite inventory.csv/evidence.json cu. 369 HIGH + 15 CRITICAL la
baseline cu, KHONG phai fresh counts. Full JSON/IDs/metadata va failed attempts
duoc luu rieng tai `.local/scan-sec01-final-*/`. Zero counts trong scan=FAIL
KHONG co nghia zero vulnerabilities. PowerShell 5 native stderr INFO bi bat
boi ErrorAction Stop; retry dung cmd stderr forwarding, khong sua G9 policy.
Cache lock errors duoc bao toan; serial retry dung cache khong con busy.

Five-image fresh scan da hoan tat, khong filter/suppress findings:

| Image | HIGH | CRITICAL | HIGH/CRITICAL co candidate fix |
| --- | ---: | ---: | ---: |
| Core | 14 | 2 | 16 |
| Gateway/APISIX | 110 | 0 | 74 |
| PostgreSQL | 95 | 4 | 25 |
| Keycloak | 89 | 7 | 96 |
| Intelligence | 61 | 2 | 0 |
| Total occurrences | 369 | 15 | 211 |

All-severity totals: UNKNOWN 6, LOW 762, MEDIUM 1111, HIGH 369, CRITICAL 15.
Fresh HIGH/CRITICAL totals bang snapshot SEC-01 cu, nhung day la run moi voi
fresh rebuilt application images. Khong nhan co remediation delta moi.
Trivy 0.76.0; vulnerability DB updated 2026-10-18 13:11:41 UTC; Java DB
updated 2026-10-18 00:58:00 UTC. Full precision tai scanner-version.txt.
Tat ca five scanned OS EOSL=false; dieu nay khong chung minh app support.
`.local/scan-sec01-final-collected/` giu validated inventory va evidence.
inventory.csv co 384 HIGH/CRITICAL occurrences; giu duplicate occurrences.
Scanner execution PASS khong phai productionGate PASS: G9 van FAIL.
Docker containerd index ID khac Trivy config ImageID: collector doi chieu
SHA256 config blob trong exact docker-save archive, luu ca hai identity;
khong bo qua identity assertion hay sua baseline exporter de che mismatch.

## E. Critical uu tien
Triage bat dau tu inventory, sau do doi chieu fresh findings va primary Spring
advisories. Runtime exploitability chua duoc chung nhan:
1. Keycloak CVE-2026-18963: inventory mo ta reset-credentials account takeover;
   candidates 26.4.15/26.6.6/26.7.2. Can verify effective recovery flow va primary
   advisory; registrationAllowed=false khong chung minh recovery an toan.
2. Spring Security CVE-2026-22732, 6.3.10: candidates 6.5.9/7.0.4. Security
   filter nam tren HTTP entry point; khong override mot jar hay nhay Security 7.
3. Spring MVC CVE-2026-47884, 6.1.21: candidate 7.0.9, XsltView/RCE theo
   inventory. Static search khong tim XsltView KHONG du de goi false positive.
4. Keycloak Netty/Bouncy Castle/FreeMarker; PostgreSQL/Intelligence native
   sqlite/zlib/libxml2: unresolved, can runtime-path/vendor-backport assessment.
Khong loai occurrence trung lap, doi severity hay tu risk accept.

Primary Spring advisories da tai thanh cong bang curl -fL (PowerShell ban dau
khong follow HTTP 308); snapshots tai `.local/sec01-final/*advisory.html`.
Security vendor cong bo same-line fix 6.3.15 la enterprise-only; MVC same-line
6.1.29 cung enterprise-only. Chua co entitlement/artifact verification cho hai
ban nay. Vendor Security workaround eager header writing thay doi cache/header
semantics; chua ap dung vi real regression dang bi chan, khong coi workaround
chua test la remediation. MVC advisory yeu cau XsltView, wildcard view rendering
va implicit view name; static absence chua thay runtime exploitability test.
Maven Central tra HTTP 200 cho Security 6.5.9 va MVC 7.0.9 POM; availability
khong chung minh compatibility. Keycloak registry manifest 26.7.2 co san;
26.4.15 va 26.6.6 khong resolve trong lan kiem tra nay. Khong tu dong nang.
Chi tiet luu primary-artifact-checks.json va keycloak-*-manifest*.json.

## F. Additional fixes
Khong patch runtime/dependency them vi chua co vendor verification va combined
regression de nang an toan. Bao toan security pins/fixes hien co va Java 17.
Khong sua auth framework, business logic, G9 policy hay baseline inventory.

## G. Findings va mitigations
Findings chua co fixed-image evidence van unresolved. Compose bind gateway/
Keycloak loopback, Intelligence khong publish host port: chi giam lab exposure,
khong phai production approval. Stateless JWT khong chung minh fix header bypass.
Chua verify effective recovery settings, TLS, exploit PoC, DB upgrade/rollback
hay maintenance entitlement. Khong tuyen bo safe/false positive/risk accepted.
Rollback candidate can known-good immutable image/BOM, owner approval va DB
compatibility; khong downgrade/restore database cua nguoi dung trong lan nay.

## H. Merge readiness
CHUA DU DIEU KIEN: scope/reconciliation hop le nhung Java/DB regression khong
PASS, full integration chua verified, independent review chua co. Van Draft.
GitHub mergeable=clean khong phai security approval.

## I. Release readiness
BLOCKED: G9 chua PASS theo policy zero HIGH/CRITICAL va no EOS OS. Can LICENSE/
data-rights approval, third-party notices, browser UI E2E, clean-source build/
startup, production hardening va owner release approval. PKCE khong thay UI E2E.
G9 FAIL tren full fresh five-image inventory, khong chi partial scan.

## J. Security Owner next actions
1. Reproduce pinned PostgreSQL tren host doc lap; kiem tra Docker content store.
   Khong prune/reset Docker hay xoa volume FE/AI.
2. Rerun Java/DB va full real integration/PKCE tren clean committed combined
   source; verify merge-ref parents truoc khi dung CI evidence.
3. Review full five-image inventory voi IDs/digests va scanner/DB timestamps;
   rerun scan sau moi remediation. Failed scan attempts van duoc giu rieng.
4. Owners verify primary advisories, supported same-major artifacts va bounded
   regression/rollback; lay independent review truoc khi de nghi merge.

## Reproduce evidence checks

```powershell
python .local/sec01-final/collect-scans.py
python scripts/security/export-inventory.py --self-test
python scripts/security/export-inventory.py .local/scan-sec01-final-collected .local/scan-sec01-final-collected/inventory.csv
git diff --check
git diff --cached --check
```

Collector assert exact archive-config SHA256 against Trivy ImageID, retained
Docker index ID, revision labels, scan coverage va per-severity counts.
Combined source export: `.local/sec01-final/combined-source.tar`, SHA256
`9985eb8cfa6196bec82029615615ae980ca927de8f7d74d46ff80b87c986837c`.
Archive nay khong co secrets/untracked report, khong phai clean checkout PASS.
