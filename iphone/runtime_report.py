"""Run in the installed Pyto to print a secret-free compatibility inventory."""

import json
import sys
from pathlib import Path


def main():
    root = str(Path(__file__).resolve().parent.parent)
    if root not in sys.path:
        sys.path.insert(0, root)
    from iphone.runtime import inventory
    print(json.dumps(inventory(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
