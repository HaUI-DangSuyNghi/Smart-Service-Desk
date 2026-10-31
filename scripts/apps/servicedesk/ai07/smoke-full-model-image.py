"""Offline GPU/HTTP proof for one built-in synthetic fixture, never customer input."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import secrets
import subprocess
import time
import uuid
from full_model_release import docker, read_json, require, verify_receipt, write_new


CLIENT = r'''
from hashlib import sha256
import http.client, json, os, time, uuid
from pathlib import Path
token = Path(os.environ['AI07_QWEN_TOKEN_FILE']).read_text('ascii').strip()
manifest = json.loads(Path('/opt/model/serving-manifest.json').read_bytes())
from local_classification.qwen_artifacts import bundle_digest
from servicedesk.intelligence.triage_ai07.qwen_policy import POLICY_DIGEST
fields = {'description':'Synthetic laptop keyboard has a broken physical key.','title':'Synthetic keyboard fault'}
payload = {'contractVersion':'classification-inference@0.1.0','requestId':str(uuid.uuid4()),
 'inputDigest':sha256(json.dumps(fields,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
 'modelDigest':bundle_digest(manifest),'policyDigest':POLICY_DIGEST,
 'allowedClasses':['ACCOUNT','HARDWARE','NETWORK','OTHER','SOFTWARE'],'input':fields}
def request(body, bearer):
 connection = http.client.HTTPConnection('127.0.0.1',8003,timeout=5)
 started=time.monotonic()
 connection.request('POST','/internal/intelligence/v1/classify',json.dumps(body).encode(),
  {'Authorization':'Bearer '+bearer,'Content-Type':'application/json'})
 response=connection.getresponse(); raw=response.read(16385)
 assert len(raw)<=16384
 result=(response.status,json.loads(raw),round((time.monotonic()-started)*1000,3))
 connection.close()
 return result
denied=request(payload,'x'*64)
assert denied[0]==403
invalid=request({**payload,'unexpectedField':True},token)
assert invalid[0]==502
status,result,elapsed=request(payload,token)
assert status==200, 'Synthetic inference did not complete within provider budget'
assert result['modelDigest']==payload['modelDigest'] and result['policyDigest']==POLICY_DIGEST
vote=result['observation']
assert result['operationalAuthority'] is False and vote['operationalAuthority'] is False
assert vote['score'] is None and vote['evidenceStatus']=='UNVERIFIED_BY_MODEL'
assert vote['decision'] in ('PROPOSE','ABSTAIN')
assert (vote['decision']=='ABSTAIN' and vote['classId'] is None) or vote['classId'] in payload['allowedClasses']
import torch
print(json.dumps({'fixture':'OWNED_SYNTHETIC_KEYBOARD','modelDigest':result['modelDigest'],
 'policyDigest':POLICY_DIGEST,'decision':vote['decision'],'classId':vote['classId'],
 'score':None,'httpStatus':status,'wallMs':elapsed,'negativeAuth':denied[0],
 'negativeSchema':invalid[0],'gpu':torch.cuda.get_device_name(0),'operationalAuthority':False}))
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()
    require(30 <= args.timeout <= 600 and not args.output.exists())
    receipt = read_json(args.image_receipt)
    verify_receipt(receipt)
    name = "ai-d2-qwen-proof-" + uuid.uuid4().hex[:12]
    secret_dir = args.output.resolve().parent / (".secrets-" + name)
    secret_dir.mkdir(parents=True)
    token_file = secret_dir / "classification-token"
    token_file.write_text(secrets.token_hex(32), encoding="ascii")
    started = False
    try:
        docker("run", "-d", "--name", name, "--label", "codex.task=ai-d2-model-proof",
               "--gpus", "all", "--network", "none", "--read-only", "--cap-drop", "ALL",
               "--security-opt", "no-new-privileges:true", "--pids-limit", "96", "--memory", "6g",
               "--shm-size", "256m", "--tmpfs", "/tmp:rw,noexec,nosuid,size=256m",
               "--mount", "type=bind,source=" + str(token_file) + ",target=/run/secrets/classification-token,readonly",
               "-e", "AI07_QWEN_ENABLED=1", "-e", "AI07_QWEN_TOKEN_FILE=/run/secrets/classification-token",
               receipt["distributionImageId"], "--bundle", "/opt/model", "--model-digest", receipt["modelDigest"],
               "--runtime-identity", receipt["codeImageId"], "--token-file", "/run/secrets/classification-token")
        started = True
        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            probe = subprocess.run(["docker", "exec", name, "python", "-m", "local_classification.qwen_lora_health"], capture_output=True)
            if probe.returncode == 0:
                break
            require(docker("inspect", name, "--format", "{{.State.Running}}").strip() == "true", "Model process exited before readiness")
            time.sleep(3)
        else:
            raise RuntimeError("Qwen did not become ready; GPU inference NOT_VERIFIED")
        result = subprocess.run(["docker", "exec", "-i", name, "python", "-"], input=CLIENT, text=True, capture_output=True, timeout=25)
        require(result.returncode == 0, "Synthetic GPU HTTP/authorization probe failed")
        proof = json.loads(result.stdout)
        require(proof["modelDigest"] == receipt["modelDigest"] and proof["policyDigest"] == receipt["policyDigest"])
        write_new(args.output, {"schemaVersion": "ai07-full-model-smoke@1.0.0", "result": "PASS",
                                "createdAt": datetime.now(timezone.utc).isoformat(),
                                "distributionImageId": receipt["distributionImageId"], "codeImageId": receipt["codeImageId"],
                                "network": "none", "hostModelMounts": False, "inference": "REAL_QWEN_LORA_SYNTHETIC_ONLY",
                                "proof": proof, "productionApproved": False})
        print("PASS actual offline Qwen LoRA GPU inference + auth/schema negatives; synthetic only; production NO_GO")
    finally:
        if started:
            docker("rm", "-f", name)
        token_file.unlink(missing_ok=True)
        secret_dir.rmdir()


if __name__ == "__main__":
    main()
