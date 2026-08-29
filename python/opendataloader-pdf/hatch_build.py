"""Custom hatch hooks: resolve the readme, and copy the JAR and license files."""

import glob
import shutil
from pathlib import Path

from hatchling.builders.hooks.plugin.interface import BuildHookInterface
from hatchling.metadata.plugin.interface import MetadataHookInterface


class CustomMetadataHook(MetadataHookInterface):
    """Resolve `readme` without requiring a pre-copied README.md.

    hatchling validates [project.readme] while parsing metadata, which happens
    *before* any build hook runs. Pointing it at a plain "README.md" therefore
    made a source install fail outright on a fresh clone, because that file is
    not tracked — build-python.sh copies it in and deletes it again:

        OSError: Readme file does not exist: README.md

    Resolving it here instead covers both layouts: the monorepo, where the
    canonical README lives two levels up, and an extracted sdist, which ships
    its own copy alongside pyproject.toml.
    """

    def update(self, metadata):
        root_dir = Path(self.root)
        for candidate in (root_dir / "README.md", root_dir / "../../README.md"):
            if candidate.exists():
                metadata["readme"] = {
                    "content-type": "text/markdown",
                    "text": candidate.read_text(encoding="utf-8"),
                }
                return
        raise FileNotFoundError(
            "No README.md found next to pyproject.toml or at the repository "
            f"root (looked in {root_dir} and {(root_dir / '../../').resolve()})."
        )


class CustomBuildHook(BuildHookInterface):
    def initialize(self, version, build_data):
        root_dir = Path(self.root)
        pkg_dir = root_dir / "src/opendataloader_pdf"
        dest_jar_dir = pkg_dir / "jar"
        dest_jar_path = dest_jar_dir / "opendataloader-pdf-cli.jar"
        license_path = pkg_dir / "LICENSE"
        notice_path = pkg_dir / "NOTICE"
        third_party_dest = pkg_dir / "THIRD_PARTY"

        readme_path = root_dir / "README.md"

        # The sdist must carry its own README: once extracted there is no
        # repository root to read from, and CustomMetadataHook runs again when
        # the wheel is built out of that sdist. Copying it here means a plain
        # `uv build` / `pip install .` works without build-python.sh having
        # staged it first.
        if not readme_path.exists():
            root_readme = root_dir / "../../README.md"
            if root_readme.exists():
                print(f"Copying README to {readme_path}")
                shutil.copy(root_readme, readme_path)

        # sdist-install code path: when users `pip install <sdist>.tar.gz`,
        # the extracted sdist already contains JAR/LICENSE/NOTICE/THIRD_PARTY
        # (force-included via [tool.hatch.build] artifacts in pyproject.toml),
        # and there is no java/ tree to rebuild from. Do not remove — sdist
        # installs would break with a spurious "mvn package" error.
        if (
            dest_jar_path.exists()
            and license_path.exists()
            and notice_path.exists()
            and third_party_dest.exists()
        ):
            print("All required files already exist (building from sdist), skipping copy")
            return

        # --- Copy JAR ---
        print(f"Root DIR: {root_dir}")
        source_jar_glob = str(
            root_dir / "../../java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar"
        )
        resolved_glob_path = Path(source_jar_glob).resolve()
        print(f"Searching for JAR file in: {resolved_glob_path}")

        source_jar_paths = glob.glob(source_jar_glob)
        if not source_jar_paths:
            raise RuntimeError(
                "Could not find the JAR file. Please run 'mvn package' in the "
                f"'java/' directory first. Searched in: {resolved_glob_path}"
            )
        if len(source_jar_paths) > 1:
            names = "\n  ".join(sorted(Path(p).name for p in source_jar_paths))
            raise RuntimeError(
                "Found more than one CLI JAR, so the right one cannot be chosen:\n  "
                f"{names}\n"
                "This usually means an earlier build left a JAR for a different "
                "version behind. Run 'mvn clean package' in the 'java/' directory "
                "to rebuild from a clean target/."
            )
        source_jar_path = source_jar_paths[0]
        print(f"Found source JAR: {source_jar_path}")

        dest_jar_dir.mkdir(parents=True, exist_ok=True)
        print(f"Copying JAR to {dest_jar_path}")
        shutil.copy(source_jar_path, dest_jar_path)

        # --- Copy LICENSE, NOTICE ---
        # README is copied by build-python.sh before this hook runs, because
        # hatchling validates [project.readme] during metadata parsing, which
        # happens before build hooks. Do not copy README here.
        shutil.copy(root_dir / "../../LICENSE", license_path)
        shutil.copy(root_dir / "../../NOTICE", notice_path)
        third_party_src = root_dir / "../../THIRD_PARTY"
        print(f"Copying THIRD_PARTY directory to {third_party_dest}")
        if third_party_dest.exists():
            shutil.rmtree(third_party_dest)
        shutil.copytree(third_party_src, third_party_dest)
