# intelligence (khối I)

Hiển thị kết quả AI **dùng chung cho mọi app**: gợi ý kèm bằng chứng/giải thích, trợ lý hỏi đáp có trích dẫn, báo lỗi khi AI không khả dụng. Không gọi API: app truyền hàm gọi API của mình vào (API AI gắn với bản ghi của app, backend kiểm quyền trên bản ghi đó).

| Component        | Dùng cho                                                                  | App truyền vào                                    |
| ---------------- | ------------------------------------------------------------------------- | ------------------------------------------------- |
| `SuggestionCard` | Gợi ý phân loại (envelope `SUCCESS`/`DEGRADED`, `needsReview`, evidence) | `fetchSuggestion`, `fieldLabels`, `evidenceLabels` |
| `AssistantPanel` | Trợ lý tri thức (`SUCCESS`/`INSUFFICIENT_EVIDENCE`, citations)            | `ask(question, signal)`, `subjectLabel`           |

```text
intelligence/
├─ index.ts             public API
├─ suggestionSchema.ts  zod + parseSuggestion (sai hợp đồng → ApiError 502)
├─ assistanceSchema.ts  zod + parseAssistance (strict, field lạ → 502)
├─ aiErrors.ts          câu báo lỗi 401/403/404/409/502/503/504
├─ useSuggestion.ts     useMutation, không cache
├─ useAssistance.ts     useMutation, huỷ request cũ khi hỏi lại/rời trang
├─ SuggestionCard.tsx
└─ AssistantPanel.tsx
```

## Luật

- Kết quả AI chỉ để tham khảo: luôn hiện "cần kiểm tra", **không** có nút áp dụng (chưa có API ghi nhận chấp nhận/sửa – B9).
- Text từ AI hiển thị dạng chữ thuần, không render HTML/markdown.
- Lỗi nào cũng nhắc người dùng vẫn xử lý thủ công được; kèm mã yêu cầu khi có.
- Service của app parse phản hồi bằng `parseSuggestion`/`parseAssistance` trước khi trả về.

## App cắm vào

```tsx
<SuggestionCard fetchSuggestion={() => triageTicket(ws, id)} fieldLabels={{ category: "Loại" }} />
<AssistantPanel ask={(q, signal) => askTicketAssistance(ws, id, q, signal)} subjectLabel="ticket" />
```
