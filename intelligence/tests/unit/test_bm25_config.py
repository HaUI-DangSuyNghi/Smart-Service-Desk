from dataclasses import FrozenInstanceError, replace

import pytest

from hdpi.domain.errors import EngineConfigurationError
from hdpi.retrieval.bm25.config import Bm25Config
from hdpi.retrieval.bm25.engine import analyze
from hdpi.retrieval.tfidf.config import vectorizer_options
from sklearn.feature_extraction.text import TfidfVectorizer


def test_config_is_frozen_and_metadata_explicit():
    config = Bm25Config()
    config.validate()
    assert config.system() == {
        'engine': 'bm25', 'engineVersion': '0.1.0', 'engineConfigVersion': 'bm25-config@0.2.0',
        'normalizationVersion': 'text-normalization@0.2.0',
        'chunkPolicyVersion': 'markdown-paragraph@0.1.0',
        'queryStrategyVersion': 'caller-provided-query@0.1.0',
        'numericalRuntime': 'cpython@3.12;unicode@15.0.0;stdlib-math@3.12',
    }
    with pytest.raises(FrozenInstanceError):
        config.k1 = 2


@pytest.mark.parametrize('change', [
    {'k1': 1.5}, {'b': 0}, {'k1': float('nan')}, {'b': float('inf')},
    {'version': 'bm25-config@unknown'}, {'token_pattern': r'\w+'}, {'ngram_range': (1, 1)},
    {'query_terms': 'count'}, {'normalization': 'changed'}, {'chunk_policy': 'changed'},
    {'query_strategy': 'changed'}, {'numerical_runtime': 'changed'}, {'length': 'words-only'},
])
def test_unversioned_behavior_mutations_rejected(change):
    with pytest.raises(EngineConfigurationError):
        replace(Bm25Config(), **change).validate()


@pytest.mark.parametrize('text', ['', '!!!', 'a bc def', 'alpha alpha beta', 'Tiếng Việt wifi', 'under_score 123'])
def test_features_match_frozen_tfidf_representation(text):
    expected = TfidfVectorizer(**vectorizer_options()).build_analyzer()(text)
    assert analyze(text, Bm25Config()) == tuple(expected)
