"""In-memory weak-supervision research, deliberately separate from approved training."""

from dataclasses import dataclass, field
from hashlib import sha256
import json
import warnings

from .decision_observation import CONTRACT, DecisionObservation, validate_observation
from .protocols import ClassificationRequest, digest, require


@dataclass(frozen=True)
class WeakExample:
    sample_id: str
    family_digest: str
    text: str = field(repr=False)
    class_id: str


class SilverResearchClassifier:
    """No operational serializer, approval conversion, calibration or promotion path."""

    def __init__(
        self,
        *,
        allowed_classes,
        dataset_digest,
        policy_digest,
        encoder=None,
        enabled=False,
    ):
        require(enabled is True and digest(dataset_digest) and digest(policy_digest))
        self.allowed_classes = tuple(allowed_classes)
        self.dataset_digest = dataset_digest
        self.policy_digest = policy_digest
        self.encoder = encoder
        self._pipeline = None
        self._manifest = None

    def fit(self, examples, *, evaluation_families):
        import numpy as np
        import sklearn
        from sklearn.exceptions import ConvergenceWarning
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.pipeline import FeatureUnion, Pipeline
        from threadpoolctl import threadpool_limits

        require(type(examples) is tuple and 2 <= len(examples) <= 100000)
        require(all(type(row) is WeakExample for row in examples))
        require(len({row.sample_id for row in examples}) == len(examples))
        require(not {row.family_digest for row in examples} & set(evaluation_families))
        require({row.class_id for row in examples} == set(self.allowed_classes))
        require(
            all(type(row.text) is str and 0 < len(row.text) <= 8192 for row in examples)
        )
        texts = [row.text for row in examples]
        estimator = LogisticRegression(C=1.0, max_iter=1000, random_state=0)
        encoder_identity = None
        if self.encoder is None:
            features = FeatureUnion(
                [
                    ("word", TfidfVectorizer(ngram_range=(1, 2), max_features=20000)),
                    (
                        "char",
                        TfidfVectorizer(
                            analyzer="char_wb", ngram_range=(3, 5), max_features=30000
                        ),
                    ),
                ]
            )
            pipeline = Pipeline([("features", features), ("classifier", estimator)])
            values = texts
        else:
            encoder_identity = {
                key: getattr(self.encoder, key)
                for key in ("name", "model_version", "artifact_digest", "dimensions")
            }
            values = np.asarray(self.encoder.encode_documents(texts), dtype=np.float64)
            require(
                values.shape == (len(texts), self.encoder.dimensions)
                and np.isfinite(values).all()
            )
            norms = np.linalg.norm(values, axis=1, keepdims=True)
            require((norms > 0).all())
            values = values / norms
            pipeline = estimator

        with threadpool_limits(limits=1), warnings.catch_warnings():
            warnings.simplefilter("error", ConvergenceWarning)
            pipeline.fit(values, [row.class_id for row in examples])

        model_hash = sha256()
        model_hash.update(json.dumps(encoder_identity, sort_keys=True).encode())
        if self.encoder is None:
            for _, vectorizer in pipeline.named_steps["features"].transformer_list:
                vocabulary = {
                    key: int(value) for key, value in vectorizer.vocabulary_.items()
                }
                model_hash.update(json.dumps(vocabulary, sort_keys=True).encode())
                model_hash.update(vectorizer.idf_.tobytes())
        model_hash.update(estimator.coef_.tobytes())
        model_hash.update(estimator.intercept_.tobytes())
        model_hash.update(json.dumps(list(estimator.classes_)).encode())
        self._pipeline = pipeline
        self._manifest = {
            "contractVersion": "weak-classifier-research@0.1.0",
            "role": "NEVER_PROMOTABLE",
            "scope": "UNREVIEWED_SILVER_POC",
            "modelStateDigest": model_hash.hexdigest(),
            "datasetDigest": self.dataset_digest,
            "policyDigest": self.policy_digest,
            "trainingExamples": len(examples),
            "trainingFamilies": len({row.family_digest for row in examples}),
            "classes": list(self.allowed_classes),
            "encoder": encoder_identity,
            "features": "TFIDF_WORD12_CHAR35"
            if self.encoder is None
            else "FROZEN_EMBEDDINGS_L2",
            "estimator": "LOGREG_C1_SEED0_MAXITER1000",
            "sklearnVersion": sklearn.__version__,
            "numpyVersion": np.__version__,
            "featureSchema": "ordered-fields-newline@0.1.0",
            "scoreSemantics": "NOT_AVAILABLE",
            "goldEligible": False,
            "officialQualityClaim": False,
            "promotion": "NO_GO",
        }
        return self

    @property
    def manifest(self):
        require(self._manifest is not None)
        return json.loads(json.dumps(self._manifest))

    def observe(self, request, *, abstain_id):
        import numpy as np

        require(type(request) is ClassificationRequest and self._pipeline is not None)
        require(
            request.allowed_classes == self.allowed_classes
            and request.policy_digest == self.policy_digest
        )
        text = "\n".join(value for _, value in request.fields)
        if self.encoder is None:
            values = [text]
        else:
            values = np.asarray(self.encoder.encode_documents([text]), dtype=np.float64)
            require(
                values.shape == (1, self.encoder.dimensions)
                and np.isfinite(values).all()
            )
            norm = np.linalg.norm(values)
            require(norm > 0)
            values = values / norm
        label = str(self._pipeline.predict(values)[0])
        observation = DecisionObservation(
            contract_version=CONTRACT,
            decision="PROPOSE",
            class_id=label,
            option_order=(*self.allowed_classes, abstain_id),
            model_scores=None,
            score_semantics="NOT_AVAILABLE",
        )
        return validate_observation(observation, request, abstain_id=abstain_id)
