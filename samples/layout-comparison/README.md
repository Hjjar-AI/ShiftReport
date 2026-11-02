# A5 layout samples

The current fixed layout is `a5-current-two-columns.pdf`, using the same six fictional patients (four filled/two empty follow-ups). Earlier optional-layout PDFs are retained as historical evidence; the current renderer no longer offers their switches. Demographics remain full width; each section uses its own content height. Read down the right column, then down the left, then continue on the next page. Diagnosis, treatment, follow-up, ordered lab groups and finally tasks retain their source order, with independent thin inset separators. No nested treatment/lab columns in the full two-column layout. Every page is marked DEMO.

Preview presentation includes current Android nominal body/heading/name sizes (12.5/13/16pt), normal-text-colored admission number beside its heading, no dashed team/admission separators, heavier dots and 12% theme-tinted section headings. Generated with the production desktop ReportLab renderer and bundled Tajawal font. Android uses Android Canvas/Paint and requires separate compilation/native PDF acceptance; these files do not establish pixel or pagination parity. No network/app/database access.

Regenerate: `PYTHONDONTWRITEBYTECODE=1 desktop/.venv/bin/python samples/generate_layout_comparison.py`.
