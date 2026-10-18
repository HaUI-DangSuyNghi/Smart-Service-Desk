export default [{
  files: ['src/platform/**/*.js'],
  rules: {
    'no-restricted-imports': ['error', {patterns: ['**/apps/**', 'apps/**', '@apps/**']}],
  },
}];
