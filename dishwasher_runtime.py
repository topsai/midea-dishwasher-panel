"""User configuration stays beside the source or Windows executable."""
import sys
from pathlib import Path

def config_path():
    location=Path(sys.executable) if getattr(sys,'frozen',False) else Path(__file__)
    return location.resolve().with_name('dishwasher.json')
