# Fictional patient CSV

`synthetic-patients-50.csv`: 50 fictional patients with unique shuffled names:18 ice-cream flavours,16 hot drinks,16 Syrian dishes. UTF-8 BOM, CSV7 metadata/22 columns, dated24 October2026; both genders, varied ages/admissions/companions/priority/badges, multiline/empty fields, long continuations and112 pending/overdue/completed tasks. Thirty records independently mix reworded clinical themes, medication/dose fragments and lab values from an owner-supplied private workbook. No source names, identifiers, dates, intact narratives or source-patient associations are retained. Doctor/roster/task-owner assignments stay blank; completed attribution is fictional historical data. These combinations describe invented cases.

`synthetic-patients-50-A5.pdf`: every page DEMO-marked;64 pages,50 links on the first index page,298 valid internal Fit destinations. Index fills downward in the rightmost column, then moves left (1–17,18–34,35–50). Fixed28pt cells fit long names from14pt. Patient details show gender, then birth year/age, optional priority, optional companion. Shared Android/desktop geometry:4mm sides,12mm top,8mm bottom;5pt body padding,4pt section gaps,2pt title/navigation padding/inset. Rounded badges retain11.5pt text with4pt horizontal gap/padding and2pt vertical padding. Treatment uses two RTL columns for4+ nonempty source lines when this saves height, with an8pt gutter and original-line boundaries preferred. Short lists and content without a height saving stay full width; long lists continue across pages. Diagnosis/type → treatment → follow-up → grouped tasks → labs; full-page navigation retained. Index, enriched card and priority card visually inspected; artifact counts/hashes in `artifact-notes.json`. Android compilation/rendering and matching-client parity remain unverified; no suites run.

For deliberate manual import into a disposable project. This patient CSV is separate from doctor-registry CSV/protected joins/encrypted backups; no app import or Telegram publication was performed. Desktop offers patient CSV export, doctor CSV import and encrypted-backup restore, without a direct patient-CSV import action.

Generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-24 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

Name selection and clinical mixing use independent seeded generators; `--generic-only` omits workbook-theme enrichment. The generator contains only curated fragments and needs no workbook access. Regenerate for the intended shift; the shift boundary remains08:30 Damascus time.
