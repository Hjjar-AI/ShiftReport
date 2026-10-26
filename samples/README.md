# Fictional patient CSV

`synthetic-patients-50.csv`: 50 fictional patients with unique shuffled names:4 ice-cream flavours,14 hot drinks,32 Syrian dishes; at most two names share the same opening word. UTF-8 BOM, CSV7 metadata/22 columns, dated24 October2026; both genders, varied ages/admissions/companions/priority/badges, multiline/empty fields, long continuations and112 pending/overdue/completed tasks. Thirty records independently mix reworded clinical themes, medication/dose fragments and lab values from an owner-supplied private workbook. No source names, identifiers, dates, intact narratives or source-patient associations are retained. Doctor/roster/task-owner assignments stay blank; completed attribution is fictional historical data. These combinations describe invented cases.

`synthetic-patients-50-A5.pdf`: every page DEMO;56pages/50first-index links/268valid Fit destinations. [Geometry/content rules](../docs/status/current-state.md#reports-and-exports); counts/hashes and selected visual inspection in `artifact-notes.json`. Android matching rendering/parity unverified; no suites/import/live run.

For deliberate manual import into a disposable project. This patient CSV is separate from doctor-registry CSV/protected joins/encrypted backups; no app import or Telegram publication was performed. Desktop offers patient CSV export, doctor CSV import and encrypted-backup restore, without a direct patient-CSV import action.

Generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-24 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

Each run uses a fresh random seed unless `--seed` is supplied; names, patient profiles, demographics, task details and clinical fragments vary. Patient fields use plain clinical wording, with DEMO identification retained on PDF pages. Name selection and clinical mixing use independent seeded generators; `--generic-only` omits workbook-theme enrichment. The generator contains only curated fragments and needs no workbook access. Regenerate for the intended shift; the shift boundary remains08:30 Damascus time.
