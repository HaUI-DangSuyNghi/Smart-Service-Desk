from dataclasses import replace
import json

import jsonschema
import pytest

from hdpi.generation.errors import GenerationConfigurationError
from hdpi.generation.model_manifest import InferenceLimits, ModelArtifact, ModelManifest


def test_manifest_roundtrip_and_schema_bind_every_field(model_setup):
    from pathlib import Path

    manifest, directory, _, _ = model_setup
    schema_path = (
        Path(__file__).resolve().parents[2] / 'docs/governance/ai06-model-manifest.schema.json'
    )
    serialized = json.dumps(manifest.to_dict()).encode()

    jsonschema.validate(json.loads(serialized), json.loads(schema_path.read_text('utf-8')))
    assert ModelManifest.from_bytes(serialized) == manifest
    manifest.verify_directory(directory)

    for field, value in (
        ('revision', 'f' * 40),
        ('quantization', 'fp16'),
        ('backend_version', '0.2.0'),
        ('tokenizer_version', '0.2.0'),
        ('serving_image', 'sha256:' + 'f' * 64),
    ):
        changes = {field: value}

        if field == 'quantization':
            changes['artifacts'] = manifest.artifacts + (
                ModelArtifact(
                    path='quant.fixture', kind='quantization', sha256='sha256:' + 'e' * 64
                ),
            )

        assert replace(manifest, **changes).digest != manifest.digest


@pytest.mark.parametrize(
    'field,value',
    [
        ('revision', 'main'),
        ('revision', 'latest'),
        ('revision', 'TO_BE_PINNED'),
        ('serving_image', 'latest'),
        ('artifacts', ()),
        ('approvals', ()),
        ('limits', {}),
        ('backend_version', 'latest'),
    ],
)
def test_manifest_rejects_missing_or_mutable_pins(model_setup, field, value):
    manifest, _, _, _ = model_setup

    with pytest.raises(GenerationConfigurationError):
        replace(manifest, **{field: value})


@pytest.mark.parametrize(
    'path', ['../weights', '/weights', 'C:/weights', 'a\\weights', 'a//weights', './weights']
)
def test_manifest_rejects_unsafe_artifact_paths(path):
    with pytest.raises(GenerationConfigurationError):
        ModelArtifact(path=path, kind='weights', sha256='sha256:' + 'a' * 64)


@pytest.mark.parametrize('mode', ['changed', 'missing', 'unlisted'])
def test_offline_verifier_checks_actual_bytes_and_inventory(model_setup, mode):
    manifest, directory, _, _ = model_setup

    if mode == 'changed':
        (directory / manifest.artifacts[0].path).write_bytes(b'DUMMY_CANARY drift')
    elif mode == 'missing':
        (directory / manifest.artifacts[0].path).unlink()
    else:
        (directory / 'unreviewed-template').write_bytes(b'DUMMY_CANARY')

    with pytest.raises(GenerationConfigurationError) as caught:
        manifest.verify_directory(directory)

    assert 'DUMMY_CANARY' not in str(caught.value)


def test_manifest_rejects_unknown_duplicate_and_missing_limit_fields(model_setup):
    manifest, _, _, _ = model_setup
    value = manifest.to_dict()
    invalid = [json.dumps(value | {'extra': True}).encode()]
    invalid.append(
        json.dumps(value).replace('"model":', '"model":"duplicate", "model":', 1).encode()
    )
    value['limits'].pop('output_tokens')
    invalid.append(json.dumps(value).encode())

    for raw in invalid:
        with pytest.raises(GenerationConfigurationError):
            ModelManifest.from_bytes(raw)


@pytest.mark.parametrize(
    'limits',
    [
        {'concurrency': True},
        {'output_tokens': 0},
        {'total_seconds': 121},
        {'context_tokens': 64},
        {'max_response_bytes': 1000000},
    ],
)
def test_manifest_enforces_resource_ceiling(limits):
    with pytest.raises(GenerationConfigurationError):
        InferenceLimits(**limits)
