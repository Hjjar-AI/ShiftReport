# Fictional patient CSV

`synthetic-patients-50.csv`: 50 beverage-named fictional patients, UTF-8 BOM, current CSV7 metadata/22 columns, dated 24 October 2026. Psychiatric/addiction/dual cases; both genders; varied ages/admissions/companions/priority/badges; empty and multiline fields; long continuation content; pending/overdue/completed tasks. No real patient data, connection settings or credentials. Doctor/roster/task-owner assignments are blank; completed-task attribution is explicitly fictional historical data.

`synthetic-patients-50-A5.pdf`: DEMO-marked desktop example; all 50 links on the first index page, 74 pages including long-content continuations. The refreshed index title is in the page header; all cells are28pt high and only long names shrink/wrap from14pt. Two explicit long-name examples are included. The updated index was visually inspected; all internal Fit destinations resolve. Detailed counts/hashes: `artifact-notes.json`. This does not establish Android rendering parity.

For deliberate manual import into a disposable project. This is patient data, not a doctor-registry CSV, protected join file or encrypted backup. It has not been imported into an app or published. Desktop currently exposes patient CSV export, doctor CSV import and encrypted-backup restore; it has no direct patient-CSV import action.

To generate a new dated/randomized copy (existing outputs are never overwritten):

```sh
python3 samples/generate_patients.py --date 2026-10-24 --seed 123 --output /tmp/synthetic-patients-50-new.csv
```

The included date is fixed. Regenerate for the intended shift when using it later; the app's shift boundary remains 08:30 Damascus time.

A5 cards group tasks under one status-grouped section, keeping the full section together when it fits a fresh card page. Optional patient alerts appear as rounded badges below the information row (high priority tinted red); no alerts section heading. Then diagnosis/type, treatment, follow-up, grouped tasks and labs. Light/dark badge cards were visually inspected. The refreshed layout caps multiple badges at half the row width, wrapping long labels beside each other without reducing11.5pt text;6pt horizontal spacing/padding and4pt vertical padding keep them separated. Diagnosis type is printed beside its own heading.
