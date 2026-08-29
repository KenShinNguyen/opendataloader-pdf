"""Tests for the hybrid server's network bind defaults.

The server has no authentication and accepts arbitrary PDF uploads that it
runs OCR and ML models over, so it must not bind every interface by default.
It used to default to 0.0.0.0; it now defaults to loopback, and warns when an
operator deliberately opts into a wider bind.

These drive `warn_if_publicly_bound` and the argparse defaults directly rather
than calling `main()`. An earlier version of this file ran `main()` end to end,
which imports torch to detect an accelerator — with the `hybrid` extra
installed that segfaulted the whole pytest process (exit 139) instead of
testing the one rule these cases are about.
"""

import pytest

from opendataloader_pdf import hybrid_server


def _warnings(caplog):
    return " ".join(r.getMessage() for r in caplog.records if r.levelname == "WARNING")


def test_default_host_is_loopback():
    """The compiled-in default must not be a wildcard bind."""
    assert hybrid_server.DEFAULT_HOST in hybrid_server._LOOPBACK_HOSTS
    assert hybrid_server.DEFAULT_HOST != "0.0.0.0"


def test_argparse_default_host_is_loopback():
    """The flag default must match the constant, not just the constant itself."""
    parser = hybrid_server.build_arg_parser()
    assert parser.parse_args([]).host == hybrid_server.DEFAULT_HOST


@pytest.mark.parametrize("host", ["127.0.0.1", "::1", "localhost"])
def test_loopback_bind_is_silent(caplog, host):
    """A local-only bind exposes nothing, so it must not nag."""
    caplog.set_level("WARNING", logger=hybrid_server.logger.name)
    hybrid_server.warn_if_publicly_bound(host, 0)
    assert "beyond localhost" not in _warnings(caplog)


def test_wildcard_bind_warns_about_exposure_and_unlimited_uploads(caplog):
    """0.0.0.0 is honoured, but the operator is told what it costs."""
    caplog.set_level("WARNING", logger=hybrid_server.logger.name)
    hybrid_server.warn_if_publicly_bound("0.0.0.0", 0)

    warnings = _warnings(caplog)
    assert "beyond localhost" in warnings
    # An unlimited upload cap matters far more once the port is reachable.
    assert "unlimited" in warnings


def test_wildcard_bind_with_size_cap_omits_the_upload_warning(caplog):
    """Setting a cap silences the upload warning but not the exposure one."""
    caplog.set_level("WARNING", logger=hybrid_server.logger.name)
    hybrid_server.warn_if_publicly_bound("0.0.0.0", 50 * 1024 * 1024)

    warnings = _warnings(caplog)
    assert "beyond localhost" in warnings
    assert "unlimited" not in warnings


def test_argparse_default_max_file_size_is_unset():
    """None, not 0: the resolver has to tell "unset" from "explicitly 0"."""
    parser = hybrid_server.build_arg_parser()
    assert parser.parse_args([]).max_file_size is None


@pytest.mark.parametrize("host", ["127.0.0.1", "::1", "localhost"])
def test_loopback_bind_stays_unlimited(host):
    """Nobody else can reach it, and the local workflow feeds it large PDFs."""
    assert hybrid_server.resolve_max_file_size_mb(host, None) == 0


def test_public_bind_gets_a_default_cap():
    """No authentication plus unbounded uploads is not a safe default."""
    resolved = hybrid_server.resolve_max_file_size_mb("0.0.0.0", None)
    assert resolved == hybrid_server.PUBLIC_BIND_MAX_FILE_SIZE_MB
    assert resolved > 0


@pytest.mark.parametrize("host", ["127.0.0.1", "0.0.0.0"])
def test_an_explicit_cap_wins_on_either_bind(host):
    assert hybrid_server.resolve_max_file_size_mb(host, 500) == 500


def test_an_explicit_zero_still_means_unlimited_on_a_public_bind():
    """Opting into unlimited uploads stays possible — it just warns."""
    assert hybrid_server.resolve_max_file_size_mb("0.0.0.0", 0) == 0
