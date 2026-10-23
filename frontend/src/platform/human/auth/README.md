# auth

Đăng nhập Keycloak bằng Authorization Code + PKCE (`oidc-client-ts`, client `dx-web`).

| File                              | Vai trò                                                                 |
| --------------------------------- | ----------------------------------------------------------------------- |
| `config.ts`                       | Đọc `VITE_OIDC_*`, cấu hình OIDC, đường dẫn callback, `safeReturnTo`    |
| `userManager.ts`                  | `UserManager` duy nhất; token lưu trong bộ nhớ; `restoreSession` khi F5 |
| `AuthProvider.tsx` + `useAuth.ts` | Trạng thái `loading / authenticated / anonymous`, `login`, `logout`     |
| `RequireAuth.tsx`                 | Bọc route cần đăng nhập                                                 |
| `CallbackPage.tsx`                | `/auth/callback`: đổi code lấy token                                    |
| `silentCallback.ts`               | `/auth/silent`: iframe đăng nhập ngầm                                   |
| `httpAuth.ts`                     | Cấp token cho `shared/api`                                              |

Token không lưu storage; F5 lấy lại bằng đăng nhập ngầm dựa vào phiên SSO của Keycloak.
