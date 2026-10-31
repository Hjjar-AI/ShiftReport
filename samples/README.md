# Fictional patient CSV

**Current format (31 October 2026):** both patient CSVs and the generator use the full23-column CSV7 header, including blank `assignmentChangedOn`. Existing clinical/task values and row order are preserved. Both supplied files and a fresh generated50-patient file pass the current production desktop decoder. The paired A5 PDF is unchanged because no displayed patient content changed; it remains a historical DEMO preview, not current Android/desktop rendering-parity proof. No app import or Telegram publication was performed. Receipt: `local/verification/sample-fixes-upload-only-2026-10-31/`.

Latest: [`synthetic-patients-50-expanded.csv`](synthetic-patients-50-expanded.csv), dated27 October2026. A fresh randomized set of50 fictional patients, with **7–30 treatment lines per patient** (615 total) and **9–25 lab lines for45 patients** (744 total); five lab fields remain empty. Treatment entries vary across20 documentation/observation themes, two-to-four independently mixed medication/dose fragments on30 records, and longer daily continuations. Each medication fragment occupies its own line. Lab panels cover CBC, electrolytes/renal function, liver function and additional glucose/HbA1c/thyroid/B12/inflammatory/lipid examples, with units and occasional pending results. Values are independently invented; these combinations are not prescribing instructions or clinical interpretations.

UTF-8 BOM/CSV7/23columns,50 unique shuffled food/drink names,112 tasks (13 completed); doctor/roster assignments remain blank. Seed, hash, exact counts and data checks: [`expanded-artifact-notes.json`](expanded-artifact-notes.json). No app import, build, test-suite or Telegram operation. No PDF has been generated for this expanded dataset.

The earlier paired dataset and PDF are retained unchanged:

`synthetic-patients-50.csv`: 50 fictional patients with unique shuffled names:4 ice-cream flavours,14 hot drinks,32 Syrian dishes; at most two names share the same opening word. UTF-8 BOM, CSV7 metadata/23 columns, dated24 October2026; both genders, varied ages/admissions/companions/priority/badges, multiline/empty fields, long continuations and112 tasks, including13completed. Thirty records independently mix reworded clinical themes, medication/dose fragments and lab values from an owner-supplied private workbook. No source names, identifiers, dates, intact narratives or source-patient associations are retained. Doctor/roster/task-owner assignments stay blank; completed attribution is fictional historical data. These combinations describe invented cases.

`synthetic-patients-50-A5.pdf`: every page DEMO;56pages/50first-index links/268valid Fit destinations. [Geometry/content rules](../docs/status/current-state.md#reports-and-exports); counts/hashes and selected visual inspection in `artifact-notes.json`. Android matching rendering/parity unverified; no suites/import/live run.

For deliberate manual import into a disposable project. This patient CSV is separate from doctor-registry CSV/protected joins/encrypted backups; no app import or Telegram publication was performed. Desktop offers patient CSV export, doctor CSV import and encrypted-backup restore, without a direct patient-CSV import action.

Generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-27 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

The updated generator creates6–12 base treatment lines, with longer continuations for five patients; optional clinical enrichment adds two-to-four separate medication lines. Lab entries use two-to-four multiline panels for45 patients. Each run uses a fresh random seed unless `--seed` is supplied; names, patient profiles, demographics, task details and clinical fragments vary. Patient fields use plain clinical wording, with DEMO identification retained on PDF pages. Name selection and clinical mixing use independent seeded generators; `--generic-only` omits workbook-theme enrichment. The generator contains only curated fragments and needs no workbook access. Regenerate for the intended shift; the shift boundary remains08:30 Damascus time.
