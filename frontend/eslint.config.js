import js from "@eslint/js";
import prettier from "eslint-config-prettier";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import { defineConfig, globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig([
  globalIgnores(["dist", "coverage"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // Tầng 2 không được biết Tầng 3
    files: ["src/platform/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": "off",
      "@typescript-eslint/no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["**/apps", "**/apps/**", "@apps/**"],
              message: "platform (Tầng 2) không được import apps (Tầng 3).",
            },
          ],
        },
      ],
    },
  },
  {
    // Component sinh từ shadcn xuất kèm biến thể (buttonVariants...), giữ nguyên để dễ cập nhật
    files: ["src/platform/shared/ui/**/*.tsx"],
    rules: { "react-refresh/only-export-components": "off" },
  },
  prettier,
]);
