"""Tests that an aborted upload never leaves a temp PDF behind.

Both upload endpoints stream the request body into a
``NamedTemporaryFile(delete=False)`` *before* entering the ``try/finally``
that unlinks it. If the stream raises part-way through (the usual cause is a
client disconnecting mid-upload), the ``finally`` never runs and the partial
PDF stays on disk forever. Repeated aborted uploads then fill the disk.

These tests drive the endpoint callables directly with an ``UploadFile`` whose
``read()`` raises, and assert nothing is left in the temp directory.
"""

import asyncio
import tempfile
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest


class _ExplodingUpload:
    """UploadFile stand-in that yields one chunk then fails, like a disconnect."""

    def __init__(self, chunks_before_failure=1):
        self._remaining = chunks_before_failure

    async def read(self, _size=-1):
        if self._remaining > 0:
            self._remaining -= 1
            return b"%PDF-1.4 partial payload"
        raise RuntimeError("client disconnected mid-upload")


@pytest.fixture
def hybrid_server_module():
    """Import hybrid_server with docling stubbed out."""
    import importlib

    with patch.dict(
        "sys.modules",
        {
            "docling": MagicMock(),
            "docling.datamodel": MagicMock(),
            "docling.datamodel.accelerator_options": MagicMock(),
            "docling.datamodel.base_models": MagicMock(),
            "docling.datamodel.pipeline_options": MagicMock(),
            "docling.document_converter": MagicMock(),
            "docling.models": MagicMock(),
            "docling.models.factories": MagicMock(),
            "uvicorn": MagicMock(),
        },
    ):
        from opendataloader_pdf import hybrid_server

        importlib.reload(hybrid_server)
        yield hybrid_server


def _endpoint(app, path):
    for route in app.routes:
        if getattr(route, "path", None) == path:
            return route.endpoint
    raise AssertionError(f"route {path} not registered")


def _temp_pdfs():
    return set(Path(tempfile.gettempdir()).glob("*.pdf"))


# The endpoints are called directly (not through TestClient) so the upload can
# fail mid-stream; that means FastAPI's parameter defaults are not resolved and
# non-file params must be supplied explicitly.
_EXTRA_KWARGS = {
    "/v1/convert/file": {"page_ranges": None},
    "/v1/profile/file": {},
}


@pytest.mark.parametrize(
    "path", ["/v1/convert/file", "/v1/profile/file"]
)
def test_aborted_upload_leaves_no_temp_file(hybrid_server_module, path):
    """A read() failure mid-upload must still unlink the temp PDF."""
    app = hybrid_server_module.create_app()
    hybrid_server_module.converter = MagicMock()

    before = _temp_pdfs()

    with pytest.raises(RuntimeError, match="client disconnected"):
        asyncio.run(
            _endpoint(app, path)(_ExplodingUpload(), **_EXTRA_KWARGS[path])
        )

    leaked = _temp_pdfs() - before
    assert not leaked, f"aborted upload leaked temp file(s): {leaked}"


def test_profile_endpoint_enforces_max_file_size(hybrid_server_module):
    """/v1/profile/file must honour the same size cap as /v1/convert/file.

    Without a cap an unauthenticated client can stream unlimited bytes to
    disk through the profiling endpoint.
    """

    class _BigUpload:
        def __init__(self, total_chunks=4):
            self._remaining = total_chunks

        async def read(self, _size=-1):
            if self._remaining > 0:
                self._remaining -= 1
                return b"x" * 1024
            return b""

    app = hybrid_server_module.create_app(max_file_size=2048)
    hybrid_server_module.converter = MagicMock()

    before = _temp_pdfs()
    response = asyncio.run(_endpoint(app, "/v1/profile/file")(_BigUpload()))

    assert response.status_code == 413
    assert not (_temp_pdfs() - before)
