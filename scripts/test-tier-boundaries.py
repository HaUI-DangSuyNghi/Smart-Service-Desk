"""Mutation checks: guards must reject actual forbidden dependencies."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('boundaries', Path(__file__).with_name('check-tier-boundaries.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class BoundaryGuardTest(unittest.TestCase):
    def check_mutation(self, path, source, expected):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / 'docs/architecture/boundary-waivers.json'
            config.parent.mkdir(parents=True)
            config.write_text('{}', 'utf-8')
            target = root / path
            target.parent.mkdir(parents=True)
            target.write_text(source, 'utf-8')
            errors = guard.violations(root)
            self.assertTrue(any(expected in error for error in errors), errors)

    def test_core_cannot_import_apps(self):
        self.check_mutation('backend/src/main/java/vn/haui/dx/core/Bad.java',
                            'import vn.haui.dx.apps.servicedesk.Ticket;', 'forbidden import')

    def test_apps_cannot_read_engine_tables(self):
        self.check_mutation('backend/src/main/java/vn/haui/dx/apps/example/Bad.java',
                            'String sql = "select * from act_ru_task";', 'internal-sql')

    def test_runtime_cannot_branch_on_app(self):
        self.check_mutation('intelligence/src/runtime/bad.py',
                            'if profile == "service-desk": pass', 'app-literal')

    def test_platform_cannot_import_app(self):
        self.check_mutation('frontend/src/platform/bad.ts',
                            'import { Ticket } from "../../apps/servicedesk/ticket";', 'platform imports apps')


if __name__ == '__main__':
    unittest.main()
