import importlib.util
import shutil
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# docling is an optional extra. CI installs it (`uv sync --extra hybrid`);
# a plain dev checkout usually has not.
_DOCLING_INSTALLED = importlib.util.find_spec("docling") is not None

_DOCLING_STUBS = (
    "docling",
    "docling.datamodel",
    "docling.datamodel.accelerator_options",
    "docling.datamodel.base_models",
    "docling.datamodel.pipeline_options",
    "docling.document_converter",
    "docling.models",
    "docling.models.factories",
)


@pytest.fixture
def hybrid_server_module():
    """Import hybrid_server, stubbing docling only when it is not installed.

    hybrid_server has no module-level docling imports — every one of them is
    inside a function — so the module always imports cleanly and never needs
    reloading. The stubs only have to be in place while a test calls into
    those functions.

    When docling *is* installed the real package is used and sys.modules is
    left alone. Substituting MagicMocks for a package whose extension modules
    are already loaded, and reloading on top of that, is what previously
    segfaulted the whole pytest process (exit 139) in CI.
    """
    from opendataloader_pdf import hybrid_server

    if _DOCLING_INSTALLED:
        yield hybrid_server
        return

    with patch.dict("sys.modules", {name: MagicMock() for name in _DOCLING_STUBS}):
        yield hybrid_server


@pytest.fixture
def input_pdf():
    return Path(__file__).resolve().parents[3] / "samples" / "pdf" / "1901.03003.pdf"


@pytest.fixture
def output_dir():
    path = (
        Path(__file__).resolve().parents[3]
        / "python"
        / "opendataloader-pdf"
        / "tests"
        / "temp"
    )
    path.mkdir(exist_ok=True)
    yield path
    shutil.rmtree(path, ignore_errors=True)
