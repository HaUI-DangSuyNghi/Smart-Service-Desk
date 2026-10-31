"""Opt-in CPU word/character TF-IDF challenger. No artifact deserialization."""

from dataclasses import dataclass, field
from hashlib import sha256
import json
import warnings

from .protocols import ClassificationProposal, ClassificationRequest, digest, require
from .validation import validate_proposal


@dataclass(frozen=True, kw_only=True)
class TrainingExample:
    sample_id: str
    family: str
    text: str = field(repr=False)
    class_id: str
    split: str
    reviewed: bool


@dataclass(frozen=True, kw_only=True)
class TrainingApproval:
    """References verified by the trusted caller; this record cannot mint approval."""

    dataset_digest: str
    rights_receipt: str
    labels_receipt: str

    def __post_init__(self):
        require(
            all(
                digest(value)
                for value in (
                    self.dataset_digest,
                    self.rights_receipt,
                    self.labels_receipt,
                )
            )
        )


def dataset_digest(examples):
    values = [
        {key: getattr(item, key) for key in TrainingExample.__dataclass_fields__}
        for item in examples
    ]
    raw = json.dumps(values, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return sha256(raw.encode("utf-8")).hexdigest()


class TfidfLogRegCandidate:
    """Explicit fit with family leakage checks; probability is uncalibrated."""

    def __init__(
        self,
        *,
        allowed_classes,
        policy_digest,
        input_fields=("input",),
        enabled=False,
        abstain_below=0.0,
    ):
        require(enabled is True)
        ClassificationRequest(
            fields=tuple((key, "validation") for key in input_fields),
            allowed_classes=allowed_classes,
            policy_digest=policy_digest,
        )
        require(type(abstain_below) is float and 0 <= abstain_below <= 1)
        self.allowed_classes = allowed_classes
        self.policy_digest = policy_digest
        self.abstain_below = abstain_below
        self.input_fields = input_fields
        self._pipeline = None
        self._manifest = None

    def fit(self, examples, *, approval):
        import sklearn
        import numpy
        import scipy
        from sklearn.exceptions import ConvergenceWarning
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.pipeline import FeatureUnion, Pipeline

        require(type(examples) is tuple and 2 <= len(examples) <= 100000)
        require(type(approval) is TrainingApproval)
        require(all(type(item) is TrainingExample for item in examples))
        require(dataset_digest(examples) == approval.dataset_digest)
        require(len({item.sample_id for item in examples}) == len(examples))
        families, contents = {}, {}

        for item in examples:
            require(type(item.sample_id) is str and bool(item.sample_id.strip()))
            require(type(item.family) is str and bool(item.family.strip()))
            require(item.class_id in self.allowed_classes and item.reviewed is True)
            require(item.split in ("TRAIN", "DEV", "HOLDOUT"))
            require(type(item.text) is str and 0 < len(item.text.strip()) <= 8192)
            normalized = " ".join(item.text.casefold().split())

            for key, registry in ((item.family, families), (normalized, contents)):
                require(key not in registry or registry[key] == item.split)
                registry[key] = item.split

        training = [item for item in examples if item.split == "TRAIN"]
        require({item.class_id for item in training} == set(self.allowed_classes))

        pipeline = Pipeline(
            [
                (
                    "features",
                    FeatureUnion(
                        [
                            (
                                "word",
                                TfidfVectorizer(ngram_range=(1, 2), max_features=20000),
                            ),
                            (
                                "char",
                                TfidfVectorizer(
                                    analyzer="char_wb",
                                    ngram_range=(3, 5),
                                    max_features=30000,
                                ),
                            ),
                        ]
                    ),
                ),
                (
                    "classifier",
                    LogisticRegression(C=1.0, max_iter=1000, random_state=0),
                ),
            ]
        )

        with warnings.catch_warnings():
            warnings.simplefilter("error", ConvergenceWarning)
            pipeline.fit(
                [item.text for item in training], [item.class_id for item in training]
            )

        # Publish only after the complete fit succeeded. Never load external pickle/joblib bytes.
        model_hash = sha256()

        for name in ("word", "char"):
            vectorizer = dict(pipeline.named_steps["features"].transformer_list)[name]
            vocabulary = json.dumps(
                {key: int(value) for key, value in vectorizer.vocabulary_.items()},
                sort_keys=True,
                separators=(",", ":"),
            )
            model_hash.update(vocabulary.encode("utf-8"))
            model_hash.update(vectorizer.idf_.tobytes())

        estimator = pipeline.named_steps["classifier"]
        model_hash.update(estimator.coef_.tobytes())
        model_hash.update(estimator.intercept_.tobytes())
        manifest = {
            "contractVersion": "0.1.0",
            "implementation": "tfidf-word12-char35-logreg@0.1.0",
            "sklearnVersion": sklearn.__version__,
            "numpyVersion": numpy.__version__,
            "scipyVersion": scipy.__version__,
            "modelStateDigest": model_hash.hexdigest(),
            "featureSchema": "caller-ordered-fields-newline-joined@0.1.0",
            "inputFields": list(self.input_fields),
            "estimatorClasses": [str(value) for value in estimator.classes_],
            "parameters": {
                "wordNgrams": [1, 2],
                "wordMaxFeatures": 20000,
                "charAnalyzer": "char_wb",
                "charNgrams": [3, 5],
                "charMaxFeatures": 30000,
                "C": 1.0,
                "maxIter": 1000,
                "randomState": 0,
                "solver": "lbfgs",
                "abstainBelow": self.abstain_below,
            },
            "datasetDigest": approval.dataset_digest,
            "policyDigest": self.policy_digest,
            "rightsReceipt": approval.rights_receipt,
            "labelsReceipt": approval.labels_receipt,
            "classes": list(self.allowed_classes),
            "scoreSemantics": "UNCALIBRATED_PROBABILITY",
            "role": "SHADOW",
        }
        self._manifest = manifest
        self._pipeline = pipeline
        return self

    @property
    def manifest(self):
        require(self._manifest is not None)
        return json.loads(json.dumps(self._manifest))

    def predict(self, request):
        require(type(request) is ClassificationRequest and self._pipeline is not None)
        require(request.allowed_classes == self.allowed_classes)
        require(request.policy_digest == self.policy_digest)
        require(tuple(key for key, _ in request.fields) == self.input_fields)
        text = "\n".join(value for _, value in request.fields)
        probabilities = self._pipeline.predict_proba([text])[0]
        index = int(probabilities.argmax())
        score = float(probabilities[index])

        if score < self.abstain_below:
            return ClassificationProposal(
                contract_version="0.1.0", decision="ABSTAIN", class_id=None
            )

        proposal = ClassificationProposal(
            contract_version="0.1.0",
            decision="PROPOSE",
            class_id=str(self._pipeline.classes_[index]),
            score=score,
            score_semantics="UNCALIBRATED_PROBABILITY",
        )
        return validate_proposal(proposal, request)
