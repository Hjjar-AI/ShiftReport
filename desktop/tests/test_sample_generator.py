"""Temporary fictional CSV generation; no clinical workspace or retained fixture mutation."""
import csv
import importlib.util
import tempfile
import unittest
from datetime import date
from pathlib import Path

SOURCE=Path(__file__).resolve().parents[2]/'samples'/'generate_patients.py'
spec=importlib.util.spec_from_file_location('sample_generator',SOURCE)
generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(generator)


class SampleGeneratorTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.directory=Path(self.directory.name)

    def generate(self,name,seed=123,generic=False):
        path=self.directory/name
        rows=generator.generate(path,date(2026,10,27),seed,mix_clinical=not generic)
        with path.open(encoding='utf-8-sig',newline='') as stream:
            next(csv.reader(stream));reader=csv.DictReader(stream);decoded=list(reader)
        self.assertEqual(generator.COLUMNS,reader.fieldnames)
        self.assertEqual(rows,decoded)
        return path,rows

    def test_seed_reproduces_multiline_csv_and_different_seed_changes_data(self):
        first,_=self.generate('one.csv');same,_=self.generate('two.csv');other,_=self.generate('three.csv',124)
        self.assertEqual(first.read_bytes(),same.read_bytes())
        self.assertNotEqual(first.read_bytes(),other.read_bytes())

    def test_patient_identity_assignments_treatment_and_lab_panels_survive_csv_round_trip(self):
        _,rows=self.generate('sample.csv')
        self.assertEqual(50,len(rows))
        for field in ('externalId','name'):self.assertEqual(50,len({r[field] for r in rows}))
        self.assertTrue(all(not r['responsibleResidentId'] and not r['responsibleSupervisorId'] for r in rows))
        self.assertTrue(all(len(r['treatmentPlan'].splitlines())>=6 for r in rows))
        self.assertTrue(any(len(r['treatmentPlan'].splitlines())>=20 for r in rows))
        labs=[r['labs'] for r in rows if r['labs']]
        self.assertEqual(45,len(labs));self.assertTrue(all(len(v.splitlines())>=8 for v in labs))
        self.assertTrue(any('CBC' in v for v in labs))
        self.assertTrue(any('mmol/L' in v for v in labs))

    def test_generic_generation_retains_rich_multiline_data_without_medication_fragments(self):
        _,rows=self.generate('generic.csv',generic=True)
        medications=tuple(generator.CLINICAL_TERMS['medications'])
        self.assertTrue(all(not any(drug in r['treatmentPlan'] for drug in medications) for r in rows))
        self.assertTrue(all(len(r['treatmentPlan'].splitlines())>=6 for r in rows))
        self.assertEqual(45,sum(bool(r['labs']) for r in rows))

    def test_existing_output_is_never_overwritten(self):
        path,_=self.generate('existing.csv');before=path.read_bytes()
        with self.assertRaises(FileExistsError):generator.generate(path,date(2026,10,28),999)
        self.assertEqual(before,path.read_bytes())
