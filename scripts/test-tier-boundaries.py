"""Mutation tests for forbidden dependencies and the exact-debt ratchet."""
from datetime import date
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('boundaries', Path(__file__).with_name('check-tier-boundaries.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
JAVA = 'backend/src/main/java/vn/haui/dx/'


class BoundaryGuardTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write(self, path, source):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, 'utf-8')

    def mutation(self, path, source, rule):
        self.write(path, source)
        errors = guard.violations(self.root)
        self.assertTrue(any(key[1] == rule for key in errors), errors)

    def test_core_cannot_import_apps_or_hpdi(self):
        for package in ('apps.example.Ticket', 'hpdi.process.api.ProcessCapability'):
            with self.subTest(package=package):
                self.mutation(JAVA + 'core/Bad.java', f'import vn.haui.dx.{package};', 'core-upper-import')

    def test_hpdi_cannot_import_apps(self):
        self.mutation(JAVA + 'hpdi/process/Bad.java', 'import vn.haui.dx.apps.example.Ticket;', 'hpdi-app-import')

    def test_lower_tiers_cannot_import_composition_root(self):
        for tier, rule in (('core', 'core-upper-import'), ('hpdi', 'hpdi-assembly-import'), ('apps', 'apps-assembly-import')):
            with self.subTest(tier=tier):
                self.mutation(JAVA + tier + '/Bad.java', 'import vn.haui.dx.assembly.DxOsApplication;', rule)

    def test_hpdi_cannot_hardcode_an_app_profile(self):
        self.mutation(JAVA + 'hpdi/intelligence/Bad.java', 'String profile = "service-desk@0.2.0";', 'hpdi-domain-literal')

    def test_app_contract_cannot_expose_core_dto(self):
        self.mutation('contracts/apps/example/openapi.yaml', "$ref: 'core.yaml#/components/schemas/ProcessHistory'", 'app-core-contract-dto')

    def test_hpdi_api_cannot_import_even_public_core(self):
        self.mutation(JAVA + 'hpdi/process/api/Bad.java', 'import vn.haui.dx.core.workflow.api.ProcessRef;', 'hpdi-private-core-import')

    def test_apps_cannot_import_core(self):
        self.mutation(JAVA + 'apps/example/Bad.java', 'import vn.haui.dx.core.workflow.api.ProcessRef;', 'app-core-import')

    def test_only_core_workflow_internals_can_use_flowable(self):
        self.mutation(JAVA + 'hpdi/process/Bad.java', 'import org.flowable.engine.RuntimeService;', 'flowable-outside-internal')

    def test_apps_cannot_use_http_transport(self):
        self.mutation(JAVA + 'apps/example/Bad.java', 'import java.net.http.HttpClient;', 'app-transport-import')

    def test_apps_cannot_read_private_or_schema_qualified_tables(self):
        for table in ('act_ru_task', 'process_scope', 'core.metadata', '"public"."example_ticket"', 'otherapp_ticket'):
            with self.subTest(table=table):
                self.mutation(JAVA + 'apps/example/Bad.java', 'String sql = """select * from ' + table + '""";', 'app-private-sql')

    def test_reserved_engine_prefix_cannot_be_claimed_by_an_application_name(self):
        self.mutation(JAVA + 'apps/act/Bad.java', 'String sql = "select * from act_ru_task";', 'app-private-sql')

    def test_new_domain_foreign_key_is_detected_without_editing_the_guard(self):
        self.write(JAVA + 'apps/crm/Lead.java', 'class Lead {}')
        self.mutation('backend/src/main/resources/db/migration/apps/crm/V8__lead.sql',
                      'CREATE TABLE crm_lead (\nid uuid REFERENCES metadata_record(id));', 'cross-owner-fk')

    def test_apps_cannot_use_private_capabilities(self):
        self.mutation(JAVA + 'apps/example/Bad.java', 'import vn.haui.dx.hpdi.process.internal.Adapter;', 'app-private-hpdi-import')

    def test_hpdi_implementation_cannot_use_core_web_controller(self):
        self.mutation(JAVA + 'hpdi/data/Bad.java', 'import vn.haui.dx.core.data.FileApi;', 'hpdi-private-core-import')

    def test_app_sql_resource_cannot_read_private_tables(self):
        self.mutation('backend/src/main/resources/apps/example/query.sql', 'select * from act_hi_procinst;', 'app-private-sql')

    def test_cross_owner_foreign_key_is_debt(self):
        self.mutation('backend/src/main/resources/db/migration/V6__bad.sql',
                      'alter table servicedesk_ticket add foreign key (process_id) references process_scope(id);', 'cross-owner-fk')

    def test_quoted_schema_cannot_hide_cross_owner_foreign_key(self):
        self.mutation('backend/src/main/resources/db/migration/V6__bad.sql',
                      'alter table "public"."servicedesk_ticket" add foreign key (process_id) references "public"."process_scope"(id);', 'cross-owner-fk')

    def test_forward_migration_resolves_historical_foreign_key_without_editing_history(self):
        self.write('backend/src/main/resources/db/migration/V5__ticket.sql',
                   'CREATE TABLE servicedesk_ticket (\nprocess_instance_id varchar(100) REFERENCES process_scope(id));')
        self.assertTrue(guard.violations(self.root))
        self.write('backend/src/main/resources/db/migration/V6__decouple.sql',
                   'ALTER TABLE servicedesk_ticket DROP CONSTRAINT unrelated;')
        self.assertTrue(guard.violations(self.root))
        self.write('backend/src/main/resources/db/migration/V6__decouple.sql',
                   'ALTER TABLE servicedesk_ticket DROP CONSTRAINT servicedesk_ticket_process_instance_id_fkey;')
        self.assertFalse(guard.violations(self.root))

    def test_core_process_resource_is_debt(self):
        self.mutation('backend/src/main/resources/processes/new-domain.bpmn20.xml',
                      '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="new-domain"/></definitions>', 'app-process-resource')

    def test_core_cannot_know_app_process_key(self):
        self.write('backend/src/main/resources/apps/example/process/example.bpmn20.xml',
                   '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="example-process"/></definitions>')
        self.mutation(JAVA + 'core/Bad.java', 'String key = "example-process";', 'core-domain-process-key')

    def test_runtime_cannot_branch_on_app(self):
        self.mutation('intelligence/src/hdpi/bad.py', 'if profile == "service_desk": pass', 'python-app-literal')

    def test_runtime_cannot_import_apps(self):
        self.mutation('intelligence/src/hdpi/bad.py', 'from apps.example import policy', 'python-app-import')

    def test_external_profile_identity_is_discovered(self):
        self.write('intelligence/src/apps/example/intelligence/registry.yaml', 'profiles:\n  fake-domain@1.0:\n    file: fake.yaml\n')
        self.mutation('intelligence/src/hdpi/bad.py', 'PROFILE = "fake-domain"', 'python-app-literal')

    def test_runtime_cannot_bundle_new_app_profile(self):
        self.mutation('intelligence/profiles/fake/profile.yaml', 'profile: fake', 'bundled-app-profile')

    def test_runtime_cannot_embed_profiles_as_package_resources(self):
        self.mutation('intelligence/src/hdpi/_resources/profiles/fake/profile.yaml', 'profile: fake', 'bundled-app-profile')

    def test_app_policy_under_src_apps_is_not_generic_runtime(self):
        self.write('intelligence/src/apps/example/intelligence/registry.yaml', 'profiles:\n  fake-domain@1.0:\n    file: fake.yaml\n')
        self.write('intelligence/src/apps/example/policy.py', 'PROFILE = "fake-domain"')
        self.assertFalse(guard.violations(self.root))

    def test_evaluation_cannot_import_apps_or_embed_application_policy(self):
        self.mutation('intelligence/src/hdpi/evaluation/bad.py', 'from apps.example import policy', 'python-app-import')
        self.mutation('intelligence/src/hdpi/evaluation/policy.py', 'PROFILE = "service-desk"', 'python-app-literal')

    def test_platform_cannot_statically_or_dynamically_import_apps(self):
        for statement in ('import { Ticket } from "../../apps/servicedesk/ticket";',
                          'const ticket = import("../../apps/servicedesk/ticket");',
                          'const ticket = require("@apps/servicedesk/ticket");'):
            with self.subTest(statement=statement):
                self.mutation('frontend/src/platform/bad.ts', statement, 'platform-app-import')

    def test_valid_public_dependencies_and_app_sql_pass(self):
        self.write(JAVA + 'hpdi/process/internal/Adapter.java', 'import vn.haui.dx.core.workflow.api.WorkflowService;')
        self.write(JAVA + 'core/workflow/internal/Runtime.java', 'import org.flowable.engine.RuntimeService;')
        self.write(JAVA + 'apps/example/Good.java', '''
            import vn.haui.dx.hpdi.process.api.ProcessCapability;
            import vn.haui.dx.hpdi.process.api.*;
            // String sql = "select * from act_ru_task";
            String message = "from must precede to";
            String sql = """with states as (select * from example_ticket)
                select * from states left join lateral (select * from example_ticket) t on true""";
        ''')
        self.assertFalse(guard.violations(self.root))

    def baseline(self):
        observed = guard.violations(self.root)
        entries = [dict(id=f'BND-DEBT-{index}', path=key[0], rule=key[1], value=key[2], occurrences=count,
                        owner='Backend Lead', targetPhase='BND-02', expires='2099-01-01')
                   for index, (key, count) in enumerate(sorted(observed.items()))]
        return dict(schemaVersion=1, source=entries, java=[])

    def save_baseline(self, baseline):
        self.write(guard.BASELINE, json.dumps(baseline))

    def test_baseline_blocks_new_target_in_same_file(self):
        path = JAVA + 'apps/example/Bad.java'
        self.write(path, 'import vn.haui.dx.core.workflow.api.ProcessRef;')
        self.save_baseline(self.baseline())
        self.assertFalse(guard.check(self.root))
        self.write(path, 'import vn.haui.dx.core.workflow.api.ProcessRef;\nimport vn.haui.dx.core.identity.api.CurrentUser;')
        self.assertTrue(any('New boundary debt' in error for error in guard.check(self.root)))

    def test_baseline_cannot_hide_extra_literal_occurrences(self):
        self.write('intelligence/src/hdpi/bad.py', 'PROFILE = "service-desk"')
        self.save_baseline(self.baseline())
        self.write('intelligence/src/hdpi/bad.py', 'PROFILE = "service-desk"\nOTHER = "service-desk"')
        self.assertTrue(any('New boundary debt' in error for error in guard.check(self.root)))

    def test_resolved_debt_must_be_removed(self):
        path = JAVA + 'apps/example/Bad.java'
        self.write(path, 'import vn.haui.dx.core.workflow.api.ProcessRef;')
        self.save_baseline(self.baseline())
        self.write(path, 'class Good {}')
        self.assertTrue(any('stale debt' in error for error in guard.check(self.root)))

    def test_strict_mode_permits_no_migration_exception(self):
        self.write(JAVA + 'apps/example/Bad.java', 'import vn.haui.dx.core.workflow.api.ProcessRef;')
        baseline = self.baseline()
        baseline['mode'] = 'strict'
        self.assertTrue(any('no migration exceptions' in error for error in guard.validate_baseline(baseline)))

    def test_invalid_expired_wildcard_duplicate_or_incomplete_debt_fails(self):
        self.write(JAVA + 'apps/example/Bad.java', 'import vn.haui.dx.core.workflow.api.ProcessRef;')
        for field, value in (('expires', '2026-10-18'), ('expires', 'yesterday'), ('owner', ''),
                             ('path', 'backend/**/Bad.java'), ('path', '../Bad.java'),
                             ('targetPhase', 'later'), ('occurrences', 0)):
            with self.subTest(field=field, value=value):
                baseline = self.baseline()
                baseline['source'][0][field] = value
                self.assertTrue(guard.validate_baseline(baseline, date(2026, 10, 18)))
        baseline = self.baseline()
        baseline['source'].append(baseline['source'][0].copy())
        self.assertTrue(guard.validate_baseline(baseline))


if __name__ == '__main__':
    unittest.main()
