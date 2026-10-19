"""Safety, overlap and vocabulary observations; never automatic relevance labels."""

import re
from collections import Counter
from itertools import combinations

from hdpi.normalization.v1 import NormalizerV1
from hdpi.retrieval.chunking import chunk_text


def tokens(text):
    return re.findall(r'\w+', NormalizerV1().normalize(text).value)


def safety_findings(text):
    findings = []
    emails = re.findall(r'\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b', text)
    if any(not email.lower().endswith('@example.invalid') for email in emails):
        findings.append('personalEmail')
    patterns = {
        'phoneNumber': r'(?<!\w)(?:\+84|0)(?:[ .-]?\d){8,10}(?!\w)',
        'studentIdentifier': r'(?i)(?:mã sinh viên|ma sinh vien|student[_ -]?id)\s*[:=]\s*[a-z0-9]{5,}',
        'privateKey': r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        'cloudAccessKey': r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
        'jwt': r'\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b',
    }
    findings.extend(name for name, pattern in patterns.items() if re.search(pattern, text))
    credentials = re.findall(
        r"""(?ix)\b(?:password|otp|access[_\ -]?token|api[_\ -]?key|recovery[_\ -]?code)
                                 \s*[:=]\s*["']?([a-z0-9_./+=-]{4,})""",
        text,
    )
    if any(value not in ('token-placeholder', 'synthetic-placeholder') for value in credentials):
        findings.append('credentialValue')
    return findings


def longest_phrase(first, second):
    """Length of the longest contiguous common token span, without raw output."""
    left, right = tokens(first), tokens(second)
    previous = [0] * (len(right) + 1)
    maximum = 0
    for word in left:
        current = [0] * (len(right) + 1)
        for j, other in enumerate(right, 1):
            if word == other:
                current[j] = previous[j - 1] + 1
                maximum = max(maximum, current[j])
        previous = current
    return maximum


def shingles(text, size=3):
    words = tokens(text)
    return {tuple(words[i : i + size]) for i in range(max(0, len(words) - size + 1))}


def jaccard(first, second):
    left, right = shingles(first), shingles(second)
    return len(left & right) / len(left | right) if left | right else 1


def analyze_corpus(bodies, queries, *, include_holdout=False):
    # Default reports expose DEV findings only; tuning tools never rank HOLDOUT.
    observed = [q for q in queries if include_holdout or q['split'] == 'DEV']
    near, leakage, docs = [], [], []
    shared = Counter()
    for identifier, body in sorted(bodies.items()):
        words = tokens(body)
        bigrams = {' '.join(pair) for pair in zip(words, words[1:])}
        shared.update(bigrams)
        chunks = chunk_text(body, source_id=identifier, source_state=('0.2.0',))
        docs.append(
            {
                'artifactId': identifier,
                'charCount': len(body),
                'wordCount': len(words),
                'vocabularyCount': len(set(words)),
                'chunkLengths': [len(c.original_text) for c in chunks],
            }
        )
    for (left, body), (right, other) in combinations(sorted(bodies.items()), 2):
        similarity = jaccard(body, other)
        if similarity >= 0.65:
            near.append({'first': left, 'second': right, 'similarity': similarity})
    for query in observed:
        text = query['title'] + '\n' + query['description']
        for identifier, body in sorted(bodies.items()):
            overlap = longest_phrase(text, body)
            if overlap > 8:
                leakage.append(
                    {
                        'queryId': query['id'],
                        'artifactId': identifier,
                        'longestPhraseWords': overlap,
                    }
                )
    return {
        'documents': docs,
        'nearDuplicates': near,
        'phraseOverlapReview': leakage,
        'sharedBigrams': [
            {'term': term, 'documentCount': count}
            for term, count in sorted(shared.items(), key=lambda p: (-p[1], p[0]))
            if count >= 3
        ],
    }
