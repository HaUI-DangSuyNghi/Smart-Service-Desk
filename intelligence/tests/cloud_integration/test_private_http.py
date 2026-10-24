import json
from threading import Thread
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

import pytest

from hdpi.assistance_http import make_server
from cloud_providers.runtime import CloudAssistanceRuntime

from .conftest import FakeClient, KEYS, payload


@pytest.mark.parametrize("authorized", [True, False])
def test_private_v02_http_keeps_token_boundary_and_bound_response(registry, authorized):
    client = FakeClient()
    runtime = CloudAssistanceRuntime(registry, client=client, environment=KEYS)
    token = "synthetic-private-token-with-32-characters"
    server = make_server(("127.0.0.1", 0), service_token=token, runtime=runtime)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    request_payload = payload(registry)
    request = Request(
        f"http://127.0.0.1:{server.server_address[1]}/internal/intelligence/v1/assistance",
        data=json.dumps(request_payload).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + (token if authorized else "foreign-token"),
        },
    )

    try:
        opener = build_opener(ProxyHandler({}))

        if not authorized:
            with pytest.raises(HTTPError) as error:
                opener.open(request, timeout=3)

            assert error.value.code == 401
            assert client.calls == []
            return

        with opener.open(request, timeout=3) as response:
            raw = response.read(131073)

        result = json.loads(raw)
        assert result["contractVersion"] == "0.2.0"
        assert result["status"] == "SUCCESS"
        assert result["routeBinding"] == request_payload["routeBinding"]
        assert (
            result["generationRuntime"]
            == request_payload["routeBinding"]["expectedRuntime"]
        )
        assert result["routeFeedback"]["providerCalls"] == 1
        assert len(client.calls) == 1
        assert token.encode() not in raw

        for secret in KEYS.values():
            assert secret.encode() not in raw
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
