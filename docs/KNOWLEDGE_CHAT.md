# Document question answering

## Assessment and implemented path

- Existing Knowledge Hub accepted TXT/Markdown; Core already stored PDF but could not extract it.
- Existing `local-api` Ask AI used ticket title/description, not knowledge retrieval. The separate
  pinned-source assistance capability is retained; document chat does not loosen its provenance checks.
- New tier-2 sidebar page: `/knowledge-chat`. It authorizes workspace documents, chunks text,
  ranks excerpts with the installed scikit-learn TF-IDF baseline, and streams the configured API
  provider's actual output. No artificial typewriter delay or browser-side API key.
- Citations link to exact authorized excerpts and a text-only preview. Unsupported evidence or
  truncated/invalid provider output is visibly incomplete, not reported as a verified answer.
  Citation provenance is checked, but this does not prove every model claim is correct.

## Permissions and existing installations

- Requester: `document:view`, including library, extracted preview and document chat.
- Agent/coordinator: `document:view`, `document:upload`, `document:download`.
- Original file endpoint now requires `document:download`; preview never returns base64 originals.
- Fresh realms use `infra/keycloak/realm-template.json`. For an existing local realm run
  `powershell -ExecutionPolicy Bypass -File scripts/sync-knowledge-permissions.ps1` and sign in again.
  This adds role mappings without resetting the realm or database. Backend permission caching lasts 60s.
- Preventing original-file download is not DRM: visible text can still be copied or photographed.

## Supported formats and deliberate limits

- TXT, Markdown, PDF with selectable text, Word DOCX. PDFBox handles PDF; bounded ZIP/SAX handles DOCX.
- No legacy `.doc`, macro-enabled Word, OCR, HTML rendering, external XML entities or embedded-file execution.
  Convert old Word files to DOCX; OCR scanned PDFs before uploading.
- Existing upload gate and 5 MiB limit remain. Preview: at most 200 PDF pages / 200000 text characters.
- One question: up to 50 documents / 1 MB extracted UTF-8; larger libraries must select a document.
  The dropdown shows the newest 100 documents. Retrieval is lexical, not semantic embeddings.
- Only top-five retrieved excerpts leave the private AI service for the configured API provider.
  Documents and questions are untrusted prompt data.
- Questions are independent; browser localStorage history is scoped by issuer, user and workspace.
  Retention is capped at 20 conversations, 100 turns each and 1 MB; older history may be dropped.
  History does not synchronize across devices and disappears when browser storage is cleared.
  Stopped answers remain incomplete. No OCR integration is included.
  No chat database, vector database, cross-workspace search or unsupported local-model RAG claims.

## Checks

- Frontend: `npm run typecheck`; Vitest `knowledgeChatStream.test.ts`; Playwright `knowledge-chat.spec.ts`.
- Backend: `CoreKnowledgeCatalogTest`, `KnowledgeTextExtractorTest`, `KnowledgeChatControllerTest`,
  `CoreAuthorizationRegressionTest` (including preview-only users denied original downloads).
- Python: `tests/classification/test_knowledge_api.py`, provider and assistance regression suites.
- SSE public contract is in `contracts/hpdi/openapi.yaml`; disable buffering in the frontend proxy.
- Committed browser checks use synthetic sessions and stub SSE; real Keycloak/Gemini acceptance
  is a separate check, not replaced by this deterministic suite.
- Pre-merge validation against develop baseline `77f4713`: build/typecheck passed; focused frontend
  tests 40 passed; Knowledge Chat Playwright 4 passed; Python retrieval/provider tests 11 passed.
  The full frontend suite has the same three Service Desk failures as baseline (176 passed versus
  baseline 164 passed). Lint has the same existing NavItem.tsx fast-refresh error as baseline.
  No baseline failure was skipped or changed. The worktree dependency junction caused Vite font
  allow-list warnings during Playwright; the production build completed successfully.
