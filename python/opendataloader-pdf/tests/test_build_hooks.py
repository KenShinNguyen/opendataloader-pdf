"""Tests for the packaging hooks in hatch_build.py.

Two install failures motivated these:

  * `readme = "README.md"` pointed at a file that is not tracked — build-
    python.sh copies it in and deletes it again — so `pip install .` on a
    fresh clone died during metadata parsing with
    "OSError: Readme file does not exist: README.md", before reaching the
    real prerequisite (a built JAR).
  * a stale JAR from an earlier version left in java/.../target/ made the
    build abort with a message that did not say how to recover.
"""

import sys
from pathlib import Path

import pytest

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE_ROOT))

from hatch_build import CustomMetadataHook  # noqa: E402


def _hook(root: Path) -> CustomMetadataHook:
    return CustomMetadataHook(str(root), {})


def test_readme_is_read_from_the_repository_root(tmp_path):
    """The monorepo layout: the canonical README lives two levels up."""
    pkg = tmp_path / "python" / "opendataloader-pdf"
    pkg.mkdir(parents=True)
    (tmp_path / "README.md").write_text("# Root readme", encoding="utf-8")

    metadata = {}
    _hook(pkg).update(metadata)

    assert metadata["readme"]["text"] == "# Root readme"
    assert metadata["readme"]["content-type"] == "text/markdown"


def test_local_readme_wins_for_an_extracted_sdist(tmp_path):
    """An extracted sdist has no repository root, only its own copy."""
    pkg = tmp_path / "python" / "opendataloader-pdf"
    pkg.mkdir(parents=True)
    (tmp_path / "README.md").write_text("# Root readme", encoding="utf-8")
    (pkg / "README.md").write_text("# Sdist readme", encoding="utf-8")

    metadata = {}
    _hook(pkg).update(metadata)

    assert metadata["readme"]["text"] == "# Sdist readme"


def test_a_missing_readme_names_both_locations(tmp_path):
    """The error has to say where it looked, not just that it failed."""
    pkg = tmp_path / "python" / "opendataloader-pdf"
    pkg.mkdir(parents=True)

    with pytest.raises(FileNotFoundError, match="No README.md found"):
        _hook(pkg).update({})


def test_the_real_readme_resolves_from_this_checkout():
    """Guards the actual layout, not just a synthetic one."""
    metadata = {}
    _hook(PACKAGE_ROOT).update(metadata)

    assert "OpenDataLoader PDF" in metadata["readme"]["text"]


def test_multiple_jars_error_says_how_to_recover(tmp_path, monkeypatch):
    """A stale JAR from an earlier version must produce actionable advice."""
    from hatch_build import CustomBuildHook

    pkg = tmp_path / "python" / "opendataloader-pdf"
    (pkg / "src" / "opendataloader_pdf").mkdir(parents=True)
    (tmp_path / "README.md").write_text("# readme", encoding="utf-8")
    target = tmp_path / "java" / "opendataloader-pdf-cli" / "target"
    target.mkdir(parents=True)
    for version in ("0.0.0", "1.2.3"):
        (target / f"opendataloader-pdf-cli-{version}.jar").write_bytes(b"x")

    with pytest.raises(RuntimeError) as excinfo:
        CustomBuildHook(str(pkg), {}, None, None, None, None).initialize("standard", {})

    message = str(excinfo.value)
    assert "opendataloader-pdf-cli-0.0.0.jar" in message
    assert "opendataloader-pdf-cli-1.2.3.jar" in message
    assert "mvn clean package" in message
