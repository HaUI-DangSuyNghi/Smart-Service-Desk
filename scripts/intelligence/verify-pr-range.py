"""Check committed PR whitespace using the actual base, without shell expansion."""
import os
import re
import subprocess


def main():
    base = os.environ.get('INTELLIGENCE_PR_BASE_SHA')
    if not base:
        base = subprocess.check_output(['git', 'merge-base', 'origin/develop', 'HEAD'], text=True).strip()
    if not re.fullmatch('[0-9a-fA-F]{40}', base):
        raise ValueError('Expected a full PR base commit SHA')
    subprocess.run(['git', 'cat-file', '-e', base + '^{commit}'], check=True)
    subprocess.run(['git', 'diff', '--check', base + '...HEAD'], check=True)
    print(f'PASS PR-range whitespace: {base}...HEAD')


if __name__ == '__main__':
    main()
