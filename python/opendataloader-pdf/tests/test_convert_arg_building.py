"""Tests for how convert() turns keyword arguments into CLI arguments.

Two failure modes motivated these:

  * numeric options were annotated ``Optional[str]`` and appended unconverted,
    so ``threads=4`` put an int in the argv list and subprocess raised
    ``TypeError: expected str, bytes or os.PathLike object, not int``;
  * the emitted guard was a truthiness test, so ``hybrid_timeout=0`` was
    dropped even though the option documents 0 as "no timeout" — the caller
    silently got the default instead.

The same truthiness bug dropped ``replace_invalid_chars=""`` (replace invalid
characters with nothing).
"""

from pathlib import Path
from unittest.mock import patch

import pytest

from opendataloader_pdf import convert_generated as cg


def build(**kwargs):
    """Run convert() and return the argv list it would hand to the JAR."""
    with patch.object(cg, "run_jar") as run_jar:
        cg.convert(**kwargs)
        return run_jar.call_args[0][0]


def test_every_argument_is_a_string():
    """subprocess rejects non-str argv entries, so nothing may slip through."""
    args = build(
        input_path="f.pdf",
        threads=4,
        hybrid_timeout=0,
        image_resolution=144.0,
        space_ratio=0.17,
    )
    assert all(isinstance(a, str) for a in args), args


@pytest.mark.parametrize(
    ("kwargs", "expected"),
    [
        ({"threads": 4}, ["--threads", "4"]),
        ({"threads": "4"}, ["--threads", "4"]),
        ({"image_resolution": 300.0}, ["--image-resolution", "300.0"]),
        ({"space_ratio": 0.17}, ["--space-ratio", "0.17"]),
    ],
)
def test_numeric_options_accept_numbers_and_strings(kwargs, expected):
    args = build(input_path="f.pdf", **kwargs)
    assert expected[0] in args
    assert args[args.index(expected[0]) + 1] == expected[1]


def test_zero_is_forwarded_not_dropped():
    """0 is a documented value for --hybrid-timeout ("no timeout")."""
    args = build(input_path="f.pdf", hybrid_timeout=0)
    assert "--hybrid-timeout" in args
    assert args[args.index("--hybrid-timeout") + 1] == "0"


def test_empty_replacement_char_is_forwarded_not_dropped():
    """--replace-invalid-chars "" means "drop them", not "use the default"."""
    args = build(input_path="f.pdf", replace_invalid_chars="")
    assert "--replace-invalid-chars" in args
    assert args[args.index("--replace-invalid-chars") + 1] == ""


def test_path_inputs_are_accepted():
    """pathlib.Path is the natural way to pass a file in modern Python."""
    args = build(input_path=Path("doc.pdf"), output_dir=Path("out"))
    assert "doc.pdf" in args[0]
    assert args[args.index("--output-dir") + 1] == "out"


def test_path_list_inputs_are_accepted():
    args = build(input_path=[Path("a.pdf"), "b.pdf"])
    assert all(isinstance(a, str) for a in args)
    assert args[:2] == ["a.pdf", "b.pdf"]


def test_omitted_options_stay_omitted():
    """None still means "not given" — no empty flags may appear."""
    args = build(input_path="f.pdf")
    assert args == ["f.pdf"]


def test_empty_list_is_treated_as_absent():
    """An empty format list must not emit `--format ''`."""
    args = build(input_path="f.pdf", format=[])
    assert "--format" not in args


def test_list_values_are_joined_with_commas():
    args = build(input_path="f.pdf", format=["markdown", "json"])
    assert args[args.index("--format") + 1] == "markdown,json"
