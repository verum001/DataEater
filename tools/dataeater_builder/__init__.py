"""Compatibility bridge. Builder implementation lives in the sibling project."""
from pathlib import Path
__path__ = [str(Path(__file__).resolve().parents[3] / "DataEater_builder/tools/dataeater_builder")]
