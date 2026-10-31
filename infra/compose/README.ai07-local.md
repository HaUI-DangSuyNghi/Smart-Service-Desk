# AI07 local classifiers: isolated Docker lab

This is an opt-in **synthetic DEV lab**, separate from the Java application.
Rules remain authoritative. No ticket writes, cloud fallback, customer data,
classification promotion or model-selected routing are enabled here.

Read [ADR-010](../../docs/architecture/ADR-010-pinned-local-decision-classifiers.md).
Docker Linux containers and NVIDIA passthrough are required for the GPU profile.
Native Ubuntu GPU and independent cross-machine reproduction remain separate checks.

## Prepare exact artifacts

Use the repository's pinned Python requirements. Ollama 0.40.8 and the two existing
downloads are supported: `tev1:0.8b-q8_0` and `gemma3:1b`. Never pull `tev1:latest`.
Find the real source store with `ollama show <tag> --modelfile`; an
`OLLAMA_MODELS` directory can contain another `models` subdirectory.

Pull the image once; the bridge builder uses an already pinned Python image:

```powershell
docker pull ollama/ollama:0.40.8@sha256:242721b8a8dbb71c38fdc0a07ed60ca78363f278d33fdf171a48728abf40743a
docker pull python:3.12.7-slim@sha256:60d9996b6a8a3689d36db740b49f4327be3be09a21122bd02fb8895abb38b50d

python scripts/apps/servicedesk/ai07/provision-local-models.py --source D:/OllamaModels/models --volume ssd-ai07-models-v14 --output .local/ai07/v14/provision.json
python scripts/apps/servicedesk/ai07/build-local-bridge.py --output .local/ai07/v14/bridge-build.json
python scripts/apps/servicedesk/ai07/configure-local-runtime.py --provision .local/ai07/v14/provision.json --bridge .local/ai07/v14/bridge-build.json --directory .local/ai07/v14/runtime --enable-synthetic-lab
```

These commands refuse existing volumes, attempt directories and receipts. Use new
names for subsequent attempts. Import copies only the two model trees and verifies
every recursive blob/hash before and after the Docker transfer. Host model files
are preserved. The temporary import server has no network access and stops before
runtime. Source weights are not downloaded again.

Tev1's source sets context 8192. Provisioning explicitly creates
`ai07-tev1-0.8b:ctx2048` with the same weights and a new parameter/manifest identity.
Both original and derived identities stay in the receipt. Source GGUF hashes:

| Artifact | Quantization | SHA-256 |
| --- | --- | --- |
| Tev1 0.8B | Q8_0 | `7daa72d2ed4fa16f5120bc1bb89713531d0f511c360e9228905ba88c092f13b5` |
| Gemma 3 1B | Q4_K_M | `2f8c3d137f2496dcb4019e17a50f2974b89c1be1a742db3c80cc86f91cafeeef` |

The GGUF embeds tokenizer identity. Windows and Linux can expose different native
manifest-index digests for the same verified GGUF; each actual API digest and all
manifest/blob hashes are recorded. Never substitute a tag name for a hash check.

Sealing gives the dedicated public model files/cache metadata read permissions
for the unprivileged bridge and removes write permissions. Runtime mounts the
whole model volume read-only. Ollama's relative `manifests-v2` links are accepted
only when they resolve to the expected content-addressed blob. A link or target
change revokes the startup integrity lease. Weights are not rehashed per request.

## Start the private runtime

Always supply the dedicated `--env-file`; no application/global `.env` is needed.
The generated file contains paths and image identities, not the service secret.
The secret lives in a separate private file and is mounted read-only.

```powershell
docker compose --env-file .local/ai07/v14/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local config --quiet
docker compose --env-file .local/ai07/v14/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local up --detach --wait --wait-timeout 180
```

No ports are published. Model and bridge bind `127.0.0.1` in a shared namespace
on one internal network. A collocated worker authenticates with the mounted
secret. The bridge runs as UID 10001, has a single worker and bounded backlog,
body, rate and inference limits. Both roots are read-only, capabilities dropped,
no-new-privileges enabled and Docker logging disabled. No Core, database,
application uploads, Docker socket or cloud key is mounted.

The base file is an **explicit CPU control**. Omitting the GPU overlay does not
certify GPU performance. With the GPU overlay, missing GPU placement fails the
result; partial offload is recorded rather than called full GPU residency.
Normal `compose.yaml` starts zero AI07 model containers. Without
`--enable-synthetic-lab`, the bridge remains disabled.

The provision-only overlay is a separate deliberate online-download alternative.
It uses the same external volume, has no public ports and must stop before runtime.
Never attach its outbound network to the final inference services. The supported
import script is preferable when the exact two models are already downloaded.

## Observe real inference and resource usage

Replace the container name if using another Compose project name.

```powershell
python scripts/apps/servicedesk/ai07/verify-local-gpu.py --provider tev1 --container ai07-local-ai07-classification-bridge-1 --output .local/ai07/v14/gpu-tev1.json
python scripts/apps/servicedesk/ai07/verify-local-gpu.py --provider gemma3 --container ai07-local-ai07-classification-bridge-1 --output .local/ai07/v14/gpu-gemma3.json
python scripts/apps/servicedesk/ai07/evaluate-local-models.py --split DEV --candidate all --container ai07-local-ai07-classification-bridge-1 --allow-draft-synthetic --warmups 10 --repeats 3 --output .local/ai07/v14/local-dev.json
python scripts/apps/servicedesk/ai07/validate-local-release.py --reports .local/ai07/v14/local-dev.json --runtime-config .local/ai07/v14/runtime/runtime.json
```

The profiler checks actual namespace, read-only mounts, absent public ports,
disabled Docker logging, denied anonymous bridge access and denied outbound
connection. Each model result must show actual context 2048 and positive VRAM in
the GPU profile. A raw `ollama run` is not adapter acceptance.

The DEV runner excludes HOLDOUT, records ten warmups per model load and three
repeats with alternating ABBA-style model order. Every invalid, unavailable and
timed-out attempt remains in the denominator. Rule snapshots are separate and
recomputed by the verifier; model decisions never replace them. Reports contain
IDs/hashes, timings, usage, placement and fixed diagnostics, not input text or
raw model output. Host NVIDIA peak samples are global, not per-process attribution.
Native latency statistics explicitly cover validated results; all-attempt wall
statistics also include failures/timeouts.

These 48 draft synthetic DEV rows are not gold. Macro-F1/calibrated accuracy stay
null and the CPU SSD model stays untrained until reviewed TRAIN and rights exist.
The release command returns production NO_GO; `--require-production` exits 2.
Independent labels/HOLDOUT, data rights, license review, SBOM/security acceptance,
hosted CI and owner release decisions cannot be minted by these scripts.

## Diagnose and stop

If startup rejects an artifact, keep the failed receipt and run the same bridge
configuration with `run --rm --no-deps ai07-classification-bridge --check` to see
a bounded startup failure. No request content is needed. Correct drift through a
new provisioning/configuration attempt; do not overwrite the frozen receipt.

```powershell
docker compose --env-file .local/ai07/v14/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local down
```

Do not add `--volumes`: retain model artifacts and all receipts. Stopping this
profile leaves the Java rule-first assistant and existing AI02/AI06 configuration
unchanged. Docker volumes do not transfer automatically to another machine;
reprovision verified artifacts or use an independently reviewed offline mirror.

## AI07 v1.5 experiments

The [v1.5 implementation and runbook](../../intelligence/docs/task/AI07/DEEP_IMPROVEMENT_V1_5.md)
adds content-free failure diagnostics, a separate Gemma classification-only G1
lane and controlled Tev T1/T2 policies. Build a new bridge and runtime directory;
keep v1.4 receipts and G0 policies frozen. These are synthetic DEV experiments
with no operational authority or production approval.
