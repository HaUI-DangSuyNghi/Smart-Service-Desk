"""Offline-only ONNX inference. No hub client, remote code or implicit downloads."""
from hashlib import sha256
from pathlib import Path

from ...domain.errors import EngineConfigurationError, EngineUnavailable
from ..models import MAX_QUERY_CHARS
from .artifacts import ARTIFACT_DIGEST, FILES, MODEL, REVISION
from .config import DenseConfig, check_dependencies


def read_artifact(directory, relative, size, checksum):
    root = Path(directory).resolve()
    path = (root / relative).resolve()
    if not path.is_relative_to(root):
        raise EngineConfigurationError('Model artifact path escapes configured directory')
    try:
        if path.stat().st_size != size:
            raise EngineConfigurationError('Model artifact size mismatch')
        content = path.read_bytes()
    except OSError:
        raise EngineUnavailable('Pinned local model artifact unavailable; provision it explicitly') from None
    if len(content) != size or sha256(content).hexdigest() != checksum:
        raise EngineConfigurationError('Model artifact checksum mismatch')
    return content


class OnnxEmbeddingProvider:
    name = 'onnx-e5-cpu'
    model_version = MODEL + '@' + REVISION
    artifact_digest = ARTIFACT_DIGEST
    dimensions = 384

    def __init__(self, directory, config=None):
        self.config = config or DenseConfig()
        self.config.validate()
        check_dependencies()
        if directory is None:
            raise EngineUnavailable('Explicit local dense model directory required')
        model, tokenizer = (read_artifact(directory, *pin) for pin in FILES)
        import onnxruntime as ort
        from tokenizers import Tokenizer

        try:
            options = ort.SessionOptions()
            options.intra_op_num_threads = 1
            options.inter_op_num_threads = 1
            options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
            options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
            options.use_deterministic_compute = True
            options.log_severity_level = 3
            # Loading verified bytes prevents later file substitution or external
            # ONNX tensor files from being silently loaded beside the graph.
            self._session = ort.InferenceSession(model, options, providers=['CPUExecutionProvider'])
            self._session.disable_fallback()
            self._tokenizer = Tokenizer.from_str(tokenizer.decode('utf-8'))
            self._tokenizer.enable_truncation(max_length=self.config.max_tokens, direction='right')
            if self._tokenizer.token_to_id('<pad>') != 1:
                raise ValueError()
            self._inputs = tuple(item.name for item in self._session.get_inputs())
            if (set(self._inputs) - {'input_ids', 'attention_mask', 'token_type_ids'}
                    or not {'input_ids', 'attention_mask'} <= set(self._inputs)
                    or 'last_hidden_state' not in {item.name for item in self._session.get_outputs()}):
                raise ValueError()
        except Exception:
            raise EngineUnavailable('Pinned local embedding model could not be loaded') from None

    def _encode(self, text, prefix):
        import numpy as np

        self.config.validate()
        if not isinstance(text, str) or len(text) > MAX_QUERY_CHARS:
            raise EngineConfigurationError('Embedding input exceeds internal resource limits')
        try:
            encoded = self._tokenizer.encode(prefix + text, add_special_tokens=True)
            values = {'input_ids': encoded.ids, 'attention_mask': encoded.attention_mask,
                      'token_type_ids': encoded.type_ids}
            feed = {name: np.asarray([values[name]], dtype=np.int64) for name in self._inputs}
            hidden = self._session.run(['last_hidden_state'], feed)[0]
        except Exception:
            raise EngineUnavailable('Local embedding inference failed') from None
        if (hidden.shape != (1, len(encoded.ids), self.dimensions) or hidden.dtype != np.float32
                or not np.isfinite(hidden).all()):
            raise EngineConfigurationError('Invalid embedding model output')
        mask = np.asarray(encoded.attention_mask, dtype=np.float64)
        if not mask.sum():
            raise EngineConfigurationError('Embedding attention mask is empty')
        pooled = (hidden[0].astype(np.float64) * mask[:, None]).sum(axis=0) / mask.sum()
        return tuple(float(value) for value in pooled)

    def encode_documents(self, texts):
        return tuple(self._encode(text, self.config.document_prefix) for text in texts)

    def encode_query(self, text):
        return self._encode(text, self.config.query_prefix)
