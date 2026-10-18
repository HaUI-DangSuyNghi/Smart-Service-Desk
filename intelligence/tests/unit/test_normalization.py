import re
import unicodedata

import pytest

from hdpi_intelligence.normalization.v1 import NormalizerV1


@pytest.mark.parametrize('original,expected', [
    ('  Wi-Fi\t wifi\nWI FI ', 'wifi wifi wifi'),
    ('(Wi-Fi), mạng!', '(wifi), mạng!'),
    ('xwifi wifixer wi-fix', 'xwifi wifixer wi-fix'),
    ('WI–FI', 'wi–fi'),
    ('TÀI KHOẢN', 'tài khoản'),
    (unicodedata.normalize('NFD', 'Tài khoản bị khóa'), 'tài khoản bị khóa'),
    ('ＳＯＦＴＷＡＲＥ', 'ｓｏｆｔｗａｒｅ'),
    ('Straße', 'strasse'),
    ('\u1100\u1161\u11a8', '각'),
    ('\n\t', ''),
])
def test_normalization_and_mapping(original, expected):
    normalizer = NormalizerV1()
    text = normalizer.normalize(original)
    assert text.value == expected
    assert text.original == original
    assert len(text.spans) == len(text.value)
    assert normalizer.normalize(original) == text
    for start, end in text.spans:
        assert original[start:end]


@pytest.mark.parametrize('original', ['Wi-Fi', 'WI\t FI', 'Ta\u0300i khoa\u0309n', '  CẢ\n\t PHÒNG  '])
def test_evidence_recovers_original(original):
    text = NormalizerV1().normalize(original)
    start, end = text.original_span(0, len(text.value))
    assert original[start:end] == original.strip()


def test_sequence_matches_whole_string_normalization():
    for original in ('\u1100\u1161\u11a8\u1100\u1161', 'a\u0315\u0300\u05ae', 'İ Wi-Fi', '\u0301a'):
        expected = re.sub(r'\s+', ' ', unicodedata.normalize('NFC', original).casefold()).strip()
        expected = re.sub(r'(?<!\w)(?:wi-fi|wi fi|wifi)(?!\w)', 'wifi', expected)
        assert NormalizerV1().normalize(original).value == expected
