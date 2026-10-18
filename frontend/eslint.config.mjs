import tseslint from 'typescript-eslint';
export default [
  {ignores: ['dist/**', 'dist-platform/**', 'node_modules/**']},
  ...tseslint.configs.recommended,
  {
    files: ['src/platform/**/*.ts'],
    rules: {
      'no-restricted-imports': 'off',
      '@typescript-eslint/no-restricted-imports': ['error', {patterns: ['**/apps/**', 'apps/**', '@apps/**']}],
    },
  },
];
