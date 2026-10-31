from copy import deepcopy
from http.server import HTTPServer
import json
from pathlib import Path
import runpy
from threading import Thread
import urllib.error
import urllib.parse
import urllib.request

import pytest

ROOT = Path(__file__).resolve().parents[4]
MODULE = runpy.run_path(str(ROOT / "scripts/intelligence/workspace-bootstrap.py"))
Bootstrap, Denied = MODULE["WorkspaceBootstrap"], MODULE["Denied"]
POLICY = json.loads(
    (ROOT / "infra/keycloak/automatic-workspaces-policy.json").read_text()
)


class FakeKeycloak:
    def __init__(self):
        self.parents = {"lab-a": {"id": "parent-a", "path": "/workspaces/lab-a"}}
        self.groups, self.permissions, self.memberships, self.writes = {}, {}, set(), []
        self.enabled = True

    def call(self, method, path, payload=None, missing=False):
        if method != "GET":
            self.writes.append((method, path, deepcopy(payload)))
        if path.startswith("/clients?clientId="):
            client = urllib.parse.unquote(path.split("=", 1)[1])
            return [{"id": client, "clientId": client}]
        if path == "/clients/dx-assessment-worker/service-account-user":
            return {
                "id": "worker",
                "username": "service-account-dx-assessment-worker",
                "enabled": self.enabled,
            }
        if path.startswith("/clients/dx-permissions/roles/"):
            name = urllib.parse.unquote(path.rsplit("/", 1)[-1])
            return {"id": "role-" + name, "name": name}
        if path.startswith("/group-by-path/workspaces/"):
            tail = path.split("/workspaces/", 1)[1]
            result = (
                self.parents.get(tail) if "/" not in tail else self.groups.get(tail)
            )
            if result is None and not missing:
                raise RuntimeError("Missing group")
            return deepcopy(result)
        if method == "POST" and path.endswith("/children"):
            parent_id = path.split("/")[2]
            workspace = next(
                name
                for name, parent in self.parents.items()
                if parent["id"] == parent_id
            )
            key = workspace + "/" + payload["name"]
            self.groups[key] = {
                "id": key.replace("/", "-"),
                "path": "/workspaces/" + key,
            }
            self.permissions[self.groups[key]["id"]] = set()
            return None
        if path.startswith("/users/worker/groups?"):
            return [{"id": group} for group in self.memberships]
        if path.startswith("/users/worker/groups/") and method == "PUT":
            self.memberships.add(path.rsplit("/", 1)[1])
            return None
        if path.startswith("/groups/") and "/role-mappings/clients/" in path:
            group = path.split("/")[2]
            if method == "POST":
                self.permissions[group].update(role["name"] for role in payload)
                return None
            return [{"name": name} for name in self.permissions[group]]
        raise AssertionError("Unexpected IAM operation")


def bootstrap(tmp_path, api):
    return Bootstrap(api, tmp_path / "state.json", POLICY)


def test_existing_and_future_workspaces_are_set_up_without_editing_a_list(tmp_path):
    api = FakeKeycloak()
    service = bootstrap(tmp_path, api)
    service.ensure("lab-a")
    assert api.permissions["lab-a-assessment-worker"] == {
        "intelligence:process",
        "task:handle",
    }
    assert api.permissions["lab-a-alert-worker"] == {"alerts:process"}
    assert api.memberships == {"lab-a-assessment-worker", "lab-a-alert-worker"}
    before = deepcopy(api.writes)
    service.ensure("lab-a")
    assert api.writes == before
    api.parents["future-c"] = {"id": "parent-c", "path": "/workspaces/future-c"}
    service.ensure("future-c")
    assert "future-c-assessment-worker" in api.memberships
    assert all(
        "/users/" not in path or "/users/worker/" in path for _, path, _ in api.writes
    )
    assert all(
        not any(role["name"] in ("task:assign", "request:view-all") for role in payload)
        for _, _, payload in api.writes
        if type(payload) is list
    )


@pytest.mark.parametrize("revoke", ["membership", "permission", "group", "worker"])
def test_revocation_is_not_undone_even_after_bootstrap_restart(tmp_path, revoke):
    api = FakeKeycloak()
    bootstrap(tmp_path, api).ensure("lab-a")
    if revoke == "membership":
        api.memberships.remove("lab-a-assessment-worker")
    elif revoke == "permission":
        api.permissions["lab-a-assessment-worker"].remove("intelligence:process")
    elif revoke == "group":
        del api.groups["lab-a/assessment-worker"]
    else:
        api.enabled = False
    before = deepcopy(api.writes)
    with pytest.raises(Denied):
        bootstrap(tmp_path, api).ensure("lab-a")
    assert api.writes == before


@pytest.mark.parametrize("workspace", ["missing", "../lab-a", "*", "", "a" * 101])
def test_invalid_or_nonexistent_workspaces_cannot_gain_permissions(tmp_path, workspace):
    api = FakeKeycloak()
    with pytest.raises(Denied):
        bootstrap(tmp_path, api).ensure(workspace)
    assert not api.writes


def test_existing_administrator_permission_decisions_are_preserved(tmp_path):
    api = FakeKeycloak()
    api.groups["lab-a/assessment-worker"] = {
        "id": "existing-worker-group",
        "path": "/workspaces/lab-a/assessment-worker",
    }
    api.permissions["existing-worker-group"] = set()
    with pytest.raises(Denied):
        bootstrap(tmp_path, api).ensure("lab-a")
    assert not api.writes


def test_existing_worker_group_can_be_joined_without_changing_human_members(tmp_path):
    api = FakeKeycloak()
    api.groups["lab-a/assessment-worker"] = {
        "id": "existing-worker-group",
        "path": "/workspaces/lab-a/assessment-worker",
    }
    api.permissions["existing-worker-group"] = {"intelligence:process", "task:handle"}
    bootstrap(tmp_path, api).ensure("lab-a")
    assert "existing-worker-group" in api.memberships
    assert not any(
        method == "POST" and "existing-worker-group/role-mappings" in path
        for method, path, _ in api.writes
    )


def test_http_requires_private_token_and_never_accepts_context_or_human_ids(tmp_path):
    api = FakeKeycloak()
    server = HTTPServer(("127.0.0.1", 0), MODULE["Handler"])
    server.token, server.bootstrap = "t" * 64, bootstrap(tmp_path, api)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        request = urllib.request.Request(base + "/internal/workspaces/lab-a", data=b"")
        with pytest.raises(urllib.error.HTTPError) as error:
            opener.open(request)
        assert error.value.code == 403 and not api.writes
        request = urllib.request.Request(
            base + "/internal/workspaces/lab-a",
            data=b'{"user":"human"}',
            headers={"Authorization": "Bearer " + server.token},
        )
        with pytest.raises(urllib.error.HTTPError) as error:
            opener.open(request)
        assert error.value.code == 403 and not api.writes
        request = urllib.request.Request(
            base + "/internal/workspaces/lab-a",
            data=b"",
            headers={"Authorization": "Bearer " + server.token},
        )
        with opener.open(request) as response:
            assert json.load(response) == {"workspace": "lab-a", "ready": True}
    finally:
        server.shutdown()
        server.server_close()
        thread.join()
