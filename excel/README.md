# ShiftReport Excel

Native Windows VBA client for the app's existing Telegram snapshots. Excel owns clinical editing; no desktop app/Python companion runs during use. Python only generates the workbook and adjacent VBA source.

- [Start/import/configuration](initiaitaion.md)
- [Original sheets and replacement logic](WORKBOOK_LOGIC.md)
- [Source work and actual limits](WORK_LOG.md)
- [Credential placeholders](credentials.example.md); actual files belong in ignored `private/`.

```bash
python3 generate_workbook.py
python3 generate_workbook.py --blank --format xlsx --output output/Blank.xlsx
```

Default: `output/ShiftReport-Excel.xlsm`, preserving original clinical cells when `private/original.xlsm` exists. VBA is supplied beside the workbook and must be imported in Excel; it is **not embedded or compiled** by the generator. `.xlsx` cannot retain VBA. Real `.xls` generation (`--format xls`) requires Windows with installed Excel; it is not a renamed OOXML file.

Target: Windows 7 SP1+ and Excel 2010+ VBA7, 32/64-bit Office, installed Tajawal fonts and working TLS 1.2/certificates. No Windows/Excel/VBA/PDF/live runtime certification has been performed. The workbook and local recovery copies contain readable clinical data; shared Telegram documents use the project's existing encryption choice, while PDF is intentionally readable.
