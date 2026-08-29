"""Tests for page_ranges validation on /v1/convert/file.

The endpoint used to parse the value with a bare split/int and swallow every
failure, leaving page_range_tuple as None — which means "convert the whole
document". So a caller who asked for "1-" or "abc" silently had the entire
PDF processed instead of the pages they wanted, and a reversed "20-10" was
handed straight to the converter. Both now fail loudly with a 400.
"""

from unittest.mock import MagicMock, patch

import pytest


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


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("1-5", (1, 5)),
        ("10-10", (10, 10)),
        (" 3 - 7 ", (3, 7)),  # padding is tolerated
        (None, None),
        ("", None),
        ("   ", None),
    ],
)
def test_valid_ranges_are_parsed(hybrid_server_module, value, expected):
    assert hybrid_server_module.parse_page_ranges(value) == expected


@pytest.mark.parametrize(
    "value",
    [
        "abc",       # not a range at all
        "1",         # single page, not START-END
        "-5",        # missing start
        "5-",        # missing end
        "1-2-3",     # too many parts
        "1.5-3",     # not an integer
        "0-5",       # pages are 1-based
        "20-10",     # reversed; used to parse and reach the converter
        "1-5x",      # trailing junk must not be ignored
    ],
)
def test_malformed_ranges_raise(hybrid_server_module, value):
    """Every one of these previously fell through to "whole document"."""
    with pytest.raises(ValueError, match="page_ranges"):
        hybrid_server_module.parse_page_ranges(value)


def _endpoint(app, path):
    for route in app.routes:
        if getattr(route, "path", None) == path:
            return route.endpoint
    raise AssertionError(f"route {path} not registered")


def test_endpoint_returns_400_for_a_malformed_range(hybrid_server_module):
    """A bad range must be rejected before the upload is even read."""
    import asyncio

    app = hybrid_server_module.create_app()
    hybrid_server_module.converter = MagicMock()

    class _Upload:
        def __init__(self):
            self.reads = 0

        async def read(self, _size=-1):
            self.reads += 1
            return b""

    upload = _Upload()
    response = asyncio.run(
        _endpoint(app, "/v1/convert/file")(upload, page_ranges="20-10")
    )

    assert response.status_code == 400
    # Rejected up front, so the body was never streamed to disk.
    assert upload.reads == 0
