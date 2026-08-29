"""Tests for the hybrid server's network bind defaults.

The server has no authentication and accepts arbitrary PDF uploads that it
runs OCR and ML models over, so it must not bind every interface by default.
It used to default to 0.0.0.0; it now defaults to loopback, and warns when an
operator deliberately opts into a wider bind.
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


def _run_main(hybrid_server, monkeypatch, argv):
    """Drive main() far enough to reach the bind, capturing uvicorn's kwargs."""
    captured = {}

    def fake_run(_app, **kwargs):
        captured.update(kwargs)

    monkeypatch.setattr("sys.argv", ["opendataloader-pdf-hybrid", *argv])
    monkeypatch.setattr(hybrid_server, "_check_dependencies", lambda: None)
    monkeypatch.setattr(hybrid_server, "create_app", lambda **kwargs: object())
    fake_uvicorn = type("FakeUvicorn", (), {"run": staticmethod(fake_run)})
    monkeypatch.setitem(__import__("sys").modules, "uvicorn", fake_uvicorn)

    hybrid_server.main()
    return captured


def test_default_host_is_loopback(hybrid_server_module):
    """The compiled-in default must not be a wildcard bind."""
    assert hybrid_server_module.DEFAULT_HOST in hybrid_server_module._LOOPBACK_HOSTS
    assert hybrid_server_module.DEFAULT_HOST != "0.0.0.0"


def test_default_run_binds_loopback_without_warning(
    hybrid_server_module, monkeypatch, caplog
):
    """Default startup binds loopback and stays quiet about exposure."""
    caplog.set_level("WARNING", logger=hybrid_server_module.logger.name)
    captured = _run_main(hybrid_server_module, monkeypatch, [])

    assert captured["host"] == hybrid_server_module.DEFAULT_HOST
    warnings = [r.message for r in caplog.records if r.levelname == "WARNING"]
    assert not any("beyond localhost" in w for w in warnings), warnings


def test_wildcard_bind_warns_about_exposure_and_unlimited_uploads(
    hybrid_server_module, monkeypatch, caplog
):
    """--host 0.0.0.0 is honoured, but the operator is told what it costs."""
    caplog.set_level("WARNING", logger=hybrid_server_module.logger.name)
    captured = _run_main(hybrid_server_module, monkeypatch, ["--host", "0.0.0.0"])

    assert captured["host"] == "0.0.0.0"
    warnings = " ".join(
        r.getMessage() for r in caplog.records if r.levelname == "WARNING"
    )
    assert "beyond localhost" in warnings
    # max-file-size defaults to unlimited, which matters far more once exposed.
    assert "unlimited" in warnings


def test_wildcard_bind_with_size_cap_omits_the_upload_warning(
    hybrid_server_module, monkeypatch, caplog
):
    """Setting a cap silences the upload warning but not the exposure one."""
    caplog.set_level("WARNING", logger=hybrid_server_module.logger.name)
    _run_main(
        hybrid_server_module, monkeypatch, ["--host", "0.0.0.0", "--max-file-size", "50"]
    )

    warnings = " ".join(
        r.getMessage() for r in caplog.records if r.levelname == "WARNING"
    )
    assert "beyond localhost" in warnings
    assert "unlimited" not in warnings
