# Samples

Fictional data and layout examples for demos and disposable projects. These values and independently mixed medication fragments are layout data, not prescribing instructions or clinical interpretations.

| Folder | Contents |
|---|---|
| [`data/`](data/patients.csv) | Current `patients.csv` and [its metadata](data/patients.metadata.json) |
| [`data/history/`](data/history/original-patients.metadata.json) | Metadata for the earlier sample; its CSV/PDF are no longer present |
| `generators/` | Python tools for patient CSVs, embedded native demos and A5 previews |
| [`previews/a5/`](previews/a5/README.md) | A5 preview source selection, renderer notes and generated PDFs when available |

## Current dataset

`data/patients.csv` is UTF-8 BOM, CSV8, 24 columns and 50 unique food/drink patients, dated 27 October 2026. It contains 112 tasks (13 completed); doctor and roster assignments are blank. The 2 November lab revision retains 138 ordered panels: 104 dated 26–27 October and 34 undated, across 45 patients; five patients have no labs. Readable `labs` agrees with `labGroupsJson`.

Each patient has 5–11 treatment lines: 342 total, comprising 263 independent medication/dose lines and 79 earlier brief notes. Medicines outnumber notes in each patient. The 1 November treatment revision changed only `treatmentPlan`; the 2 November lab revision changed only `labs` and `labGroupsJson`. The 45 populated lab fields have 9–25 lines each, 744 total. Seeds, revisions and recorded checks remain in the metadata; moving the CSV did not change its bytes.

Android and the archived desktop demo embed this dataset; the web demo reads it from disk. This patient CSV is separate from doctor-registry CSVs, protected joins and encrypted backups. Manual import belongs in a disposable project. The desktop has patient CSV export, doctor CSV import and encrypted-backup restore, without a direct patient-CSV import action.

## Generators

Run from the repository root. These commands document explicit actions; reorganizing the files did not execute them.

```sh
# Generate a separate dated sample; existing output is never overwritten.
python3 samples/generators/generate_patients.py --date 2026-10-27 --seed 123 --output /tmp/patients-new.csv

# Refresh embedded native demo sources after an intentional dataset edit.
python3 samples/generators/generate_android_demo.py
python3 samples/generators/generate_desktop_demo.py

# Render the current fixed two-column A5 preview with desktop dependencies.
PYTHONDONTWRITEBYTECODE=1 old/desktop/.venv/bin/python samples/generators/generate_layout_comparison.py
```

The patient generator defaults to `data/generated-patients.csv` and creates 4–6 medication lines plus 1–2 notes; five longer examples have eight medication lines and three notes. It uses two-to-four multiline lab panels for 45 patients. A fresh random seed is used unless supplied; names and clinical mixing use independent seeded generators. `--generic-only` omits workbook-theme enrichment. The generator uses curated fragments and needs no workbook access. Use the intended report date; the report-day boundary is 08:30 Damascus time. Builds and PDF rendering still require their usual explicit approval.

## Historical evidence

The earlier CSV7 correction retained clinical content and row order while adding blank `assignmentChangedOn`; its recorded receipt is `local/verification/sample-fixes-upload-only-2026-10-31/`. Historical metadata describes that earlier dataset, not current CSV8 acceptance.

Earlier optional A5 layouts were named `a5-current-stacked.pdf`, `a5-lab-groups-two-columns.pdf` and `a5-all-sections-two-columns.pdf`. Those PDFs are absent from this checkout; the six-patient selection and [renderer details](previews/a5/README.md) are retained. The current generator writes `previews/a5/a5-current-two-columns.pdf`, marked DEMO on every page. Desktop rendering does not establish Android pagination or visual parity. No sample generation, app import, build, PDF render, test run or Telegram publication was performed for this reorganization.
