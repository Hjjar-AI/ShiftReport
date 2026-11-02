# Fictional patient CSV

**Current expanded sample (2 November 2026):** CSV8/24 columns include138 ordered lab groups:104 dated panels (26–27 October) and34 panels with no date, across45 patients; five patients have no labs. Each group has a title and multiline results, demonstrating CBC, renal/electrolyte, liver and additional panels. The readable `labs` text matches `labGroupsJson`. Both demos embed this same dataset. Regenerate with `python3 samples/generate_android_demo.py` and `python3 samples/generate_desktop_demo.py`.

**Historical format correction (31 October 2026):** both patient CSVs and the generator use the full23-column CSV7 header, including blank `assignmentChangedOn`. Existing clinical/task values and row order are preserved. Both supplied files and a fresh generated50-patient file pass the current production desktop decoder. The paired A5 PDF is unchanged because no displayed patient content changed; it remains a historical DEMO preview, not current Android/desktop rendering-parity proof. No app import or Telegram publication was performed. Receipt: `local/verification/sample-fixes-upload-only-2026-10-31/`.

Latest: [`synthetic-patients-50-expanded.csv`](synthetic-patients-50-expanded.csv), dated27 October2026, with treatment examples revised1 November2026. Fifty fictional patients now have **5–11 treatment lines each** (342 total): **263 medication/dose lines** followed by79 brief notes retained from the earlier generated wording. Medicines outnumber notes in every record, with each medicine on its own line (for example, `Olanzapine 10 mg` or `Haloperidol 5 mg`). Medication fragments are independently pooled, never copied as a source-patient regimen. The1 November treatment revision changed only `treatmentPlan`; the2 November lab revision changes only `labs`/`labGroupsJson`. **9–25 lab lines for45 patients** (744 total) retain their fictional results with dated panel headings; five lab fields remain empty. Values and combinations are fictional layout data, not prescribing instructions or clinical interpretations.

UTF-8 BOM/CSV8/24columns,50 unique shuffled food/drink names,112 tasks (13 completed); doctor/roster assignments remain blank. Seed, hash, exact counts and data checks: [`expanded-artifact-notes.json`](expanded-artifact-notes.json). No app import, build, test-suite or Telegram operation. No PDF has been generated for this expanded dataset.

The earlier sample CSV/PDF are no longer present; historical receipts do not describe the current dataset.

For deliberate manual import into a disposable project. This patient CSV is separate from doctor-registry CSV/protected joins/encrypted backups; no app import or Telegram publication was performed. Desktop offers patient CSV export, doctor CSV import and encrypted-backup restore, without a direct patient-CSV import action.

Generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-27 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

The generator creates4–6 separate medication/dose lines plus1–2 earlier documentation notes; five longer examples use8 medication lines and3 notes. Optional clinical enrichment mixes diagnosis/follow-up themes without duplicating medication entries. Lab entries use two-to-four multiline panels for45 patients. Each run uses a fresh random seed unless `--seed` is supplied; names, patient profiles, demographics, task details and clinical fragments vary. Patient fields use plain clinical wording, with DEMO identification retained on PDF pages. Name selection and clinical mixing use independent seeded generators; `--generic-only` omits workbook-theme enrichment. The generator contains only curated fragments and needs no workbook access. Regenerate for the intended shift; the shift boundary remains08:30 Damascus time.

Historical optional A5 layouts using the same six fictional patients: [stacked](layout-comparison/a5-current-stacked.pdf), [two-column lab groups](layout-comparison/a5-lab-groups-two-columns.pdf), [two-column clinical sections](layout-comparison/a5-all-sections-two-columns.pdf). [Renderer/reading-order details](layout-comparison/README.md). The current renderer uses only two clinical columns; the generator now produces `a5-current-two-columns.pdf` while retaining these historical files. Generate the current layout with `PYTHONDONTWRITEBYTECODE=1 desktop/.venv/bin/python samples/generate_layout_comparison.py`.
