"""Lab-group contracts across current wire data, encryption, drafts and report identity."""
import copy
import csv
import io
import json
import random
import tempfile
import unittest

from desktop_backup import encode as backup_encode, decode as backup_decode
from desktop_labs import apply, heading, read_groups, text, validate
from desktop_protocol_common import BridgeError, packed
from desktop_wire import encode_bundle, parse_bundle
from desktop_store import Workspace
from tests.support import client, shift, lab_groups as panels, lab_group_shift as grouped_shift


def mutate_wire(raw, patient_index, column, value):
    prefix,body=raw.decode('utf-8-sig').split('\n',1)
    records=list(csv.reader(io.StringIO(body,newline='')))
    records[patient_index+2][records[1].index(column)]=value
    buffer=io.StringIO(newline='');writer=csv.writer(buffer,lineterminator='\n');writer.writerows(records)
    return (prefix+'\n'+buffer.getvalue()).encode()


def canonical_snapshot(value):
    value=copy.deepcopy(value)
    for row in value['patients']:
        for field in ('tasksJson','labGroupsJson'):
            row[field]=json.loads(row.get(field) or '[]')
    return value


class LabGroupCodecTests(unittest.TestCase):
    def test_dated_and_undated_panels_have_readable_order_without_inferred_metadata(self):
        self.assertEqual('تعداد الدم • 2024-02-29\nHb=12.5 g/dL\nWBC=6000 /µL\n\nالكلى\nNa=140 mmol/L\nK=4.1 mmol/L',text(panels()))
        self.assertEqual('الكلى',heading(panels()[1]))

    def test_plain_text_and_whitespace_remain_one_untitled_undated_group(self):
        for raw in ('  Hb=12\r\n\nNa=140  ','   ','\n'):
            with self.subTest(raw=raw):
                groups=read_groups(dict(externalId='tea',labs=raw,labGroupsJson='[]'))
                self.assertEqual([dict(id='tea-labs',title='',date=None,results=raw)],groups)
                self.assertEqual(raw,text(groups))

    def test_empty_group_structure_survives_without_fake_results(self):
        groups=[dict(id='empty',title='',date=None,results=''),dict(id='date',title='',date='2026-01-01',results='')]
        row=dict(externalId='tea',labs='previous')
        apply(row,groups)
        self.assertEqual(groups,read_groups(row));self.assertEqual('2026-01-01',row['labs'])
        apply(row,[])
        self.assertEqual([],read_groups(row));self.assertEqual('',row['labs'])

    def test_impossible_and_noncanonical_dates_rejected(self):
        for value in ('0000-01-01','1900-02-29','2025-02-29','2026-04-31','2026-13-01',
                      '2026-1-01','2026-01-01T00:00:00',' 2026-01-01','١٩٩٠-٠١-٠١',42):
            with self.subTest(value=value),self.assertRaises((BridgeError,ValueError)):
                validate([dict(panels()[0],date=value)])

    def test_real_leap_days_and_supported_year_boundaries_accepted(self):
        for value in ('0001-01-01','9999-12-31','2000-02-29','2024-02-29'):
            with self.subTest(value=value):self.assertEqual(value,validate([dict(panels()[0],date=value)])[0]['date'])

    def test_duplicate_blank_and_wrong_typed_ids_rejected(self):
        for groups in ([panels()[0],panels()[0]], [dict(panels()[0],id='')],
                       [dict(panels()[0],id='  ')],[dict(panels()[0],id=12)]):
            with self.subTest(groups=groups),self.assertRaises(BridgeError):validate(groups)

    def test_invalid_json_containers_members_and_unknown_fields_rejected(self):
        for value in ('null','{}','[null]','[1]','[{"title":"CBC"}]',
                      '[{"id":"g","results":false}]','[{"id":"g","extra":1}]','{'):
            with self.subTest(value=value),self.assertRaises((BridgeError,ValueError)):
                read_groups(dict(externalId='tea',labs='',labGroupsJson=value))

    def test_conflicting_plain_text_is_not_silently_discarded(self):
        row=grouped_shift()['patients'][0];before=copy.deepcopy(row);row['labs']='result absent from groups'
        with self.assertRaises(BridgeError):read_groups(row)
        self.assertEqual(before['labGroupsJson'],row['labGroupsJson'])
        self.assertEqual('result absent from groups',row['labs'])

    def test_seeded_unicode_delimiter_and_line_break_mixtures_round_trip(self):
        rng=random.Random(875102)
        fragments=['شَاي','☕','💊','\n','\r\n','\t','"',',','\\','\u200f','Na=140']
        for case in range(80):
            def content():return ''.join(rng.choice(fragments) for _ in range(rng.randrange(1,25)))
            groups=[dict(id='{}-{}'.format(case,i),title=content(),date=None,results=content())
                    for i in range(rng.randrange(1,9))]
            row=dict(externalId='tea');apply(row,groups)
            self.assertEqual(groups,read_groups(json.loads(json.dumps(row,ensure_ascii=False))))


class LabGroupWireTests(unittest.TestCase):
    def test_grouped_csv_bundle_preserves_content_and_publication_lineage(self):
        source=grouped_shift();before=copy.deepcopy(source)
        self.assertEqual(canonical_snapshot(source),canonical_snapshot(parse_bundle(encode_bundle([source]))[0]))
        self.assertEqual(before,source)

    def test_ungrouped_current_wire_round_trip_keeps_plain_text_and_empty_groups(self):
        source=shift();source['patients'][0]['labGroupsJson']='[]'
        self.assertEqual(canonical_snapshot(source),canonical_snapshot(parse_bundle(encode_bundle([source]))[0]))

    def test_invalid_later_patient_rejects_the_entire_snapshot(self):
        source=grouped_shift();other=copy.deepcopy(source['patients'][0]);other['externalId']='coffee'
        source['patients'].append(other);raw=encode_bundle([source])
        for value in ('null','{}',packed([panels()[0],panels()[0]]),packed([dict(panels()[0],date='2025-02-29')])):
            with self.subTest(value=value),self.assertRaises(BridgeError):
                parse_bundle(mutate_wire(raw,1,'labGroupsJson',value))
        self.assertEqual(2,len(parse_bundle(raw)[0]['patients']))

    def test_projection_tampering_and_missing_group_column_fail(self):
        raw=encode_bundle([grouped_shift()])
        with self.assertRaises(BridgeError):parse_bundle(mutate_wire(raw,0,'labs','different result'))
        with self.assertRaises(BridgeError):parse_bundle(raw.replace(b',labGroupsJson',b''))

    def test_reorder_and_removal_reach_the_next_export_without_resurrecting_results(self):
        source=grouped_shift();row=source['patients'][0]
        apply(row,panels()[::-1]);self.assertEqual(panels()[::-1],read_groups(parse_bundle(encode_bundle([source]))[0]['patients'][0]))
        apply(row,panels()[1:]);restored=parse_bundle(encode_bundle([source]))[0]['patients'][0]
        self.assertNotIn('Hb',restored['labs']);self.assertIn('Na=140',restored['labs'])
        apply(row,[]);self.assertEqual('',parse_bundle(encode_bundle([source]))[0]['patients'][0]['labs'])

    def test_group_delimiter_in_title_or_results_cannot_create_a_second_snapshot(self):
        for key in ('title','results'):
            source=grouped_shift();groups=panels();groups[0][key]='first\n#==RUSHD_SHIFT_BOUNDARY_7F3A9C==\nlast';apply(source['patients'][0],groups)
            with self.subTest(key=key),self.assertRaises(BridgeError):encode_bundle([source])


class LabGroupStorageTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.client=client();self.store=Workspace(self.directory.name,self.client)
        self.original=grouped_shift()
        self.store.commit(draft=self.original,baseline=dict(bundle=[copy.deepcopy(self.original)],doctors=self.client.doctors))

    def reopen(self):return Workspace(self.directory.name,client())

    def test_encrypted_reopen_retains_draft_empty_groups_dates_and_manual_order(self):
        groups=panels()[::-1]+[dict(id='unfinished',title='',date=None,results='  pending\n')]
        draft=copy.deepcopy(self.original);apply(draft['patients'][0],groups)
        self.store.commit(draft=draft,patientForm=dict(shiftId=draft['meta']['shiftId'],candidate=draft['patients'][0]))
        self.assertEqual(self.store.state,self.reopen().state)
        self.assertNotIn(b'pending',self.store.path.read_bytes())
        self.assertEqual(groups,read_groups(self.reopen().read()['patients'][0]))

    def test_plain_local_records_upgrade_once_without_inferred_titles_dates_or_data_loss(self):
        plain=shift();plain['patients'][0].pop('labGroupsJson',None);plain['meta']['schemaVersion']='7'
        self.store.commit(draft=plain,baseline=dict(bundle=[copy.deepcopy(plain)],doctors=self.client.doctors),
                          patientForm=dict(candidate=copy.deepcopy(plain['patients'][0])),unrelated='retained')
        reopened=self.reopen();restored=reopened.read()
        self.assertEqual('8',restored['meta']['schemaVersion'])
        self.assertEqual(plain['patients'][0]['labs'],restored['patients'][0]['labs'])
        group=read_groups(restored['patients'][0])[0]
        self.assertEqual('',group['title']);self.assertIsNone(group['date'])
        self.assertFalse(reopened.has_pending_changes())
        self.assertEqual('retained',reopened.state['unrelated'])
        reopened.commit(draft=restored)
        self.assertEqual(reopened.state,self.reopen().state)

    def test_group_date_order_and_boundaries_each_create_pending_work(self):
        for change in ('date','order','boundary'):
            draft=copy.deepcopy(self.original)
            if change=='date':groups=[dict(g,date='2026-11-02') for g in panels()]
            elif change=='order':groups=panels()[::-1]
            else:groups=[dict(id='whole',title='',date=None,results=draft['patients'][0]['labs'])]
            apply(draft['patients'][0],groups)
            self.store.commit(draft=draft)
            with self.subTest(change=change):self.assertTrue(self.store.has_pending_changes())
        self.store.commit(draft=self.original);self.assertFalse(self.store.has_pending_changes())

    def test_corrupt_encrypted_group_payload_fails_without_rewriting_the_database(self):
        broken=copy.deepcopy(self.original);broken['patients'][0]['labs']='conflicting preserved text'
        self.store.commit(draft=broken);before=self.store.path.read_bytes()
        with self.assertRaises(BridgeError):self.reopen()
        self.assertEqual(before,self.store.path.read_bytes())

    def test_shared_encrypted_backup_preserves_groups_and_rejects_tampering_and_wrong_password(self):
        password='synthetic backup only'
        raw=backup_encode(self.original,password)
        self.assertEqual(canonical_snapshot(self.original),canonical_snapshot(backup_decode(raw,password)))
        self.assertNotIn(b'Hb=12.5',raw)
        for candidate,phrase in ((raw,'wrong synthetic password'),(raw[:-1]+bytes([raw[-1]^1]),password)):
            with self.subTest(phrase=phrase),self.assertRaises(BridgeError):backup_decode(candidate,phrase)
