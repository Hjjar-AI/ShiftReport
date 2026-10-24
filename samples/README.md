# Fictional patient CSV

`synthetic-patients-50.csv`: 50 beverage-named fictional patients, UTF-8 BOM, current CSV7 metadata/22 columns, dated 24 October 2026. Psychiatric/addiction/dual cases; both genders; varied ages/admissions/companions/priority/badges; empty and multiline fields; long continuation content; pending/overdue/completed tasks. No real patient data, connection settings or credentials. Doctor/roster/task-owner assignments are blank; completed-task attribution is explicitly fictional historical data.

`synthetic-patients-50-A5.pdf`: DEMO-marked desktop example; all 50 links on the first index page, 86 pages including long-content continuations. Index/card pages were visually inspected; all internal Fit destinations resolve. Detailed counts/hashes: `artifact-notes.json`. This does not establish Android rendering parity.

For deliberate manual import into a disposable project. This is patient data, not a doctor-registry CSV, protected join file or encrypted backup. It has not been imported into an app or published. Desktop currently exposes patient CSV export, doctor CSV import and encrypted-backup restore; it has no direct patient-CSV import action.

To generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-24 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

The included date is fixed. Regenerate for the intended shift when using it later; the app's shift boundary remains 08:30 Damascus time.
