"""Launch the local Jinja client; compatible with CPython 3.8.10+."""

import argparse
import sys
import threading
import webbrowser


def main():
    if sys.version_info < (3, 8, 10):
        raise SystemExit("ShiftReport Web requires Python 3.8.10 or newer.")
    parser = argparse.ArgumentParser(description="ShiftReport local browser client")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    options = parser.parse_args()
    if not 1024 <= options.port <= 65535:
        parser.error("--port must be between 1024 and 65535")

    try:
        from waitress import create_server
        from shiftreport import create_app
    except ModuleNotFoundError:
        raise SystemExit(
            "Dependencies are missing. Use this Python environment to run "
            "python -m pip install -r requirements.txt from web/."
        ) from None

    app = create_app()
    try:
        server = create_server(app, host="127.0.0.1", port=options.port, threads=4)
    except OSError:
        raise SystemExit("Cannot open the local port. Try --port 8766.") from None
    url = "http://127.0.0.1:{}/".format(options.port)
    print("ShiftReport DEMO: {} (Ctrl+C to stop)".format(url))
    if not options.no_browser:
        threading.Thread(target=webbrowser.open, args=(url,), daemon=True).start()
    try:
        server.run()
    except KeyboardInterrupt:
        pass
    finally:
        server.close()


if __name__ == "__main__":
    main()
