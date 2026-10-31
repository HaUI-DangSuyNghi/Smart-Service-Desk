# Licensing - Apache-2.0 for authorized team-owned work

## Approval and scope

On 2026-10-31, the team leader confirmed that all three BE, FE and AI members
approved Apache License 2.0 for components the team has the right to license.
Root `LICENSE` contains the unmodified official Apache License 2.0 text.
The grant covers authorized original team-owned source, contracts, schemas,
scripts, infrastructure and technical documentation, not others' ownership.
Keep existing third-party licenses, attribution and required notices unchanged.

## Final submission approval

In the 2026-10-31 FINAL SUBMISSION instruction, the team leader additionally
confirmed the team's distribution rights for the reviewed asset scope. This
owner/maintainer statement is the recorded basis, not an independently verified
permission receipt from HaUI or an external copyright holder. No external owner,
license identifier, permission letter or attribution condition is invented.

The release manifest `docs/publication/release-distribution-status.csv` enumerates
all 82 exact paths and SHA-256 values. Its statuses distinguish:

- `TEAM_APPROVED_DISTRIBUTION`: the 78 reviewed fixture/schema/derived files,
  approved for distribution on the basis of the team leader's confirmation.
  This is not a claim that external incorporated content has been relicensed.
- `EXTERNAL_TERMS_CONFIRMATION_REQUIRED`: the four supplied document/photo
  files below. The team leader asserts distribution rights, but the applicable
  external permission terms/owner attribution are not supplied in this record.
  Do not mark them as externally licensed or declare RELEASE GO until resolved.

| External content | Still needed before public release |
| --- | --- |
| `docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf` | Applicable owner/issuer redistribution permission and conditions |
| `docs/references/Ke_hoach_DX_Lab_14_ngay_3_nguoi.pdf` | Identify original ownership and redistribution basis/conditions |
| `docs/references/Phan_cong_DX_Lab_14_ngay_3_nguoi.xlsx` | Identify original ownership and redistribution basis/conditions |
| `frontend/public/landing/banner-phong-may.jpg` | Photo source, owner/license or permission, and required attribution |

Original ownership remains with the respective rights holders. Distribution
permission is not transfer of copyright or automatic Apache-2.0 relicensing.
The team grant does not override third-party, university or contest conditions.

## Release manifest versus evaluation manifests

The release CSV is the current distribution-approval manifest. Payload bytes,
checksums and existing evaluation manifests remain unchanged. Their pending
human-review/internal-evaluation/ingestion states describe frozen worksheets,
not a grant of runtime ingestion or a reviewed-gold benchmark. Public submission
approval does not imply `APPROVED_INTERNAL_EVAL`, model-training rights or a
change to runtime evidence gates. Do not rewrite embedded digest chains or
silently mark unreviewed gold as reviewed just to update distribution metadata.

## Third-party components and history

Dependencies, generated/copied third-party UI, fonts/icons, containers, models,
weights, datasets and provider content retain their own terms. `licenses/**`
is preserved verbatim; this release does not bundle model weights or images.
A blanket project NOTICE is not a substitute for required upstream notices.

The existing repository/history is retained. Tip deletion or filtering a release
attachment would not remove older public copies. No asset was removed or history
rewritten. Resolve the four external entries' permission conditions for this
public distribution, including the retained historical copies, before publishing.
Older pending-source-grant statements in governance documents are historical;
this document and the dated release manifest record the current scoped decision.
