from pathlib import Path

def default_pdf_fonts():
    return [Path(__file__).resolve().parents[1] / "static/fonts/tajawal_regular.ttf"]
