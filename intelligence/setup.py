"""Bundle authoritative profile/schema files without a second committed copy."""

from pathlib import Path
import shutil

from setuptools import setup
from setuptools.command.build_py import build_py
from setuptools.command.sdist import sdist

ROOT = Path(__file__).resolve().parent


def contracts():
    # An sdist carries its own snapshot; a repo build uses the shared contracts.
    snapshot = ROOT / '_contract_resources'
    return snapshot if snapshot.is_dir() else ROOT.parent / 'contracts/intelligence/v1'


class BuildPy(build_py):
    def run(self):
        super().run()
        destination = Path(self.build_lib) / 'hdpi/_resources'
        # A wheel rebuilt in the same worktree must not retain old bundled profiles.
        stale = (destination / 'profiles').resolve()
        if stale.is_dir():
            if not stale.is_relative_to(Path(self.build_lib).resolve()):
                raise RuntimeError('Resource path escapes the build directory')
            shutil.rmtree(stale)
        for name, source in [('contracts', contracts())]:
            shutil.copytree(source, destination / name, dirs_exist_ok=True)


class SourceDistribution(sdist):
    def make_release_tree(self, base_dir, files):
        super().make_release_tree(base_dir, files)
        shutil.copytree(contracts(), Path(base_dir) / '_contract_resources', dirs_exist_ok=True)


setup(cmdclass={'build_py': BuildPy, 'sdist': SourceDistribution})
