#!/usr/bin/env python3
"""Create fictional patient CSV data for deliberate manual import (Python 3.8+)."""
import argparse
import base64
import csv
import json
import random
import uuid
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path

COLUMNS = ('externalId,admitNum,admitDate,gender,name,birthDate,hasCompanion,'
           'diagnosisType,initialDiagnosis,treatmentPlan,followUp,labs,responsibleResidentId,'
           'responsibleSupervisorId,warningFlags,warningDetails,isPriority,lastEditedByDoctorId,'
           'lastEditedBy,updatedAt,revision,tasksJson').split(',')
NAME_POOLS = {
    'ice_cream': ['بوظة عربية', 'مثلجات الفستق الحلبي', 'آيس كريم الفانيليا', 'سوربيه الليمون'],
    'hot_drinks': [
        'شاي أحمر', 'شاي أخضر', 'شاي بالنعناع', 'شاي بالهيل', 'شاي بالقرفة',
        'شاي بالليمون', 'قهوة عربية', 'قهوة تركية', 'قهوة بالحليب', 'قهوة بالهيل',
        'قهوة بيضاء', 'كابتشينو', 'لاتيه', 'كاكاو ساخن', 'شوكولا ساخنة',
        'سحلب', 'سحلب بالقرفة', 'حليب بالعسل', 'حليب بالزنجبيل', 'يانسون',
        'بابونج', 'زهورات شامية', 'زنجبيل بالليمون', 'كمون وليمون',
    ],
    'syrian_food': [
        'كبة مقلية', 'كبة مشوية', 'كبة لبنية', 'كبة بالصينية', 'كبة سماقية',
        'كبة سفرجلية', 'كبة حيلة', 'شيش برك', 'شاكرية', 'حراق إصبعه',
        'مجدرة', 'محشي كوسا', 'يبرق', 'ملوخية', 'فريكة بالدجاج', 'أوزي',
        'فتة حمص', 'فتة باذنجان', 'فول مدمس', 'مسبحة', 'فتوش', 'تبولة',
        'محمرة', 'متبل باذنجان', 'صفيحة شامية', 'لحم بعجين', 'شاورما دجاج',
        'كباب حلبي', 'سمبوسك بالجبنة', 'برك بالسبانخ', 'مناقيش زعتر',
        'مناقيش جبنة', 'قطايف بالجوز', 'معمول بالتمر', 'مهلبية',
        'أرز بالحليب', 'حلاوة الجبن', 'كنافة نابلسية', 'عش البلبل', 'برازق',
    ],
}
NAME_COUNTS = {'ice_cream': 4, 'hot_drinks': 14, 'syrian_food': 32}


def patient_names(seed):
    """Shuffle varied dishes/drinks without letting one name family dominate."""
    rng = random.Random("patient-names/{}".format(seed))
    names = []
    for category, count in NAME_COUNTS.items():
        candidates = list(NAME_POOLS[category])
        rng.shuffle(candidates)
        chosen, families = [], {}
        for name in candidates:
            family = name.split()[0]
            if families.get(family, 0) >= 2:
                continue
            chosen.append(name)
            families[family] = families.get(family, 0) + 1
            if len(chosen) == count:
                break
        if len(chosen) != count:
            raise ValueError('Not enough varied names in ' + category)
        names.extend(chosen)
    rng.shuffle(names)
    return names


CASES = [
    ('PSYCHIATRIC', 'أعراض اكتئابية مع اضطراب النوم والشهية'),
    ('PSYCHIATRIC', 'نشاط زائد مع قلة النوم وسرعة الكلام'),
    ('PSYCHIATRIC', 'أفكار غير واقعية مع تشتت الانتباه'),
    ('PSYCHIATRIC', 'قلق ونوبات هلع متقطعة'),
    ('PSYCHIATRIC', 'أفكار وسواسية وسلوكيات متكررة'),
    ('PSYCHIATRIC', 'كوابيس واسترجاع أحداث وضغط نفسي'),
    ('PSYCHIATRIC', 'صعوبات معرفية لدى مسن مع حاجة لتقييم إضافي'),
    ('ADDICTION', 'متابعة أعراض انسحاب الكحول'),
    ('ADDICTION', 'متابعة أعراض انسحاب الأفيونات'),
    ('ADDICTION', 'استخدام منبهات مع اضطراب النوم'),
    ('DUAL', 'أعراض مزاجية مترافقة مع استخدام مواد'),
    ('DUAL', 'أعراض ذهانية مترافقة مع استخدام مواد'),
]

# Common clinical terms and dose/lab fragments from the owner's private workbook.
# Phrases are newly written. Dose/lab fragments are pooled independently; no source rows survive.
CLINICAL_TERMS = {
    'symptoms': ['اضطراب النوم', 'تغير الشهية', 'أعراض قلق متقطعة',
                 'أفكار وسواسية متكررة', 'تفاوت التعاون أثناء المقابلة'],
    'medications': {
        'olanzapine': ['10 mg', '15 mg', '20 mg'],
        'haloperidol': ['10 mg', '20 mg'],
        'quetiapine': ['50 mg', '600 mg'],
        'valproate': ['500 mg', '1000 mg', '1500 mg'],
        'clozapine': ['150 mg'], 'sertraline': ['25 mg', '50 mg'],
        'diazepam': ['10 mg', '20 mg'], 'lorazepam': ['3 mg', '6 mg'],
    },
    'labs': {
        'Hb': ['12.3', '12.7', '13.1', '13.6', '14.2', '14.6', '15.1', '15.4'],
        'WBC': ['4100', '4500', '5000', '6600', '11600', '13100'],
        'ALT': ['11', '33', '38', '52'], 'AST': ['18', '44', '67', '69'], 'Cr': ['1.1'],
    },
    'observations': ['النوم متقطع', 'الشهية متبدلة',
                     'التعاون جزئي أثناء المقابلة', 'القلق أقل مقارنة بالتقييم السابق',
                     'استمرار الأفكار الوسواسية'],
}


def mix_clinical_fragments(row, rng):
    """Combine independent, reworded themes; retain no source-patient linkage."""
    symptoms = rng.sample(CLINICAL_TERMS['symptoms'], 2)
    medications = rng.sample(list(CLINICAL_TERMS['medications']), 2)
    dose_labels = [drug + ' ' + rng.choice(CLINICAL_TERMS['medications'][drug]) for drug in medications]
    observations = rng.sample(CLINICAL_TERMS['observations'], 3)
    row['initialDiagnosis'] += '؛ ' + ' مع '.join(symptoms) + '.'
    row['treatmentPlan'] = (' + '.join(dose_labels)
                            + '\n' + row['treatmentPlan'])
    if row['followUp']:
        row['followUp'] = '\n'.join(observations)
    if row['labs']:
        row['labs'] = ('تحاليل:\n'
                       + '، '.join(key + '=' + rng.choice(values) for key, values in CLINICAL_TERMS['labs'].items()))


def packed(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def generate(path, day, seed, mix_clinical=True):
    rng = random.Random(seed)
    clinical_rng = random.Random("clinical-fragments/{}".format(seed))
    names = patient_names(seed)
    namespace = uuid.uuid5(uuid.NAMESPACE_URL, 'shiftreport-synthetic-50/{}/{}'.format(day, seed))
    identity = lambda label: str(uuid.uuid5(namespace, label))
    now = datetime.combine(day, time(12), timezone(timedelta(hours=3)))
    epoch = lambda value: int(value.timestamp() * 1000)
    rows = []
    for position, number in enumerate(rng.sample(range(1, 51), 50), 1):
        kind, case = CASES[(number - 1) % len(CASES)]
        row = dict.fromkeys(COLUMNS, '')
        task_count = 8 if number in (10, 30, 50) else (number - 1) % 5
        tasks = []
        for index in range(task_count):
            done = index % 4 == 3
            due = None if index % 5 == 4 else now + timedelta(hours=rng.choice([-30, -6, 2, 10, 28]))
            tasks.append(dict(id=identity('task-{}-{}'.format(number, index)),
                description=rng.choice([
                    'توثيق ساعات النوم', 'مراجعة نتائج التقييم', 'إعادة تقييم الشهية',
                    'التواصل مع المرافق', 'تحضير ملخص المتابعة']),
                ownerDoctorId=None, ownerName=None, priority=rng.choice(['LOW', 'NORMAL', 'HIGH']),
                dueAtEpochMillis=epoch(due) if due else None,
                dueShiftDate=day.isoformat() if due and index % 3 == 0 else None,
                done=done, completedByDoctorId=identity('fictional-completer') if done else None,
                completedByName='د. الأرز' if done else None,
                completedAtEpochMillis=epoch(now - timedelta(hours=1)) if done else None))
        badges = []
        if number % 3 == 0:
            badges.append(('يحتاج مراجعة مبكرة', 'high'))
        if number % 4 == 0:
            badges.append(('تواصل مع المرافق', 'medium'))
        if number % 7 == 0:
            badges.append(('متابعة انتظام النوم والشهية والتواصل مع المرافق', 'low'))
        treatment = 'مراجعة التقييم والاستجابة وتسجيل الملاحظات اليومية.'
        if number % 10 == 0:
            treatment += '\n' + '\n'.join('اليوم {}: {}'.format(i, rng.choice([
                'تقييم النوم والشهية والتعاون أثناء المقابلة، مع توثيق التغير مقارنة باليوم السابق.',
                'متابعة الاستجابة للخطة العلاجية والتحمل، وتسجيل الملاحظات خلال الجولة.',
                'مراجعة الأعراض الحالية ونتائج التحاليل، ومناقشة المتابعة مع الفريق.']))
                                         for i in range(1, 19))
        row.update(externalId=identity('patient-{}'.format(number)), admitNum='SR-{:03d}'.format(position),
            admitDate=(day - timedelta(days=rng.randrange(0, 46))).isoformat(),
            gender='F' if number % 2 == 0 else 'M',
            name=names[position - 1],
            birthDate=str(day.year - rng.randrange(18, 87)), hasCompanion='Y' if number % 3 == 0 else 'N',
            diagnosisType=kind, initialDiagnosis=case,
            treatmentPlan=treatment,
            followUp='' if number % 6 == 0 else 'النوم {} ساعات، الشهية {}، والاستجابة {}.'.format(
                rng.randrange(2, 10), rng.choice(['جيدة', 'متوسطة', 'ضعيفة']), rng.choice(['تحسن', 'ثبات', 'تحتاج إعادة تقييم'])),
            labs='' if number % 4 == 0 else 'Hb={}، Na={}، K={}'.format(
                rng.choice(['11.2', '13.5', '15.0']), rng.choice(['132', '139', '144']), rng.choice(['3.2', '4.1', '4.8'])),
            warningFlags='|'.join(level for text, level in badges),
            warningDetails='badges:' + ';'.join(level + ':' + base64.urlsafe_b64encode(text.encode()).decode().rstrip('=')
                for text, level in badges) if badges else '',
            isPriority='Y' if number % 5 == 0 else 'N', updatedAt=str(epoch(now)), revision='0',
            tasksJson=packed(tasks))
        if mix_clinical and number % 5 in (1, 2, 3):
            mix_clinical_fragments(row, clinical_rng)
        rows.append(row)
    meta = '# schemaVersion=7 shiftDate={} shiftId={} doctors= sortSpec={{"levels":[]}} shiftRevision=0 snapshotId= baseSnapshotId= deviceId='.format(
        day, identity('shift'))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8-sig', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow([meta])
        writer.writerow(COLUMNS)
        writer.writerows([[row[column] for column in COLUMNS] for row in rows])
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'synthetic-patients-50.csv')
    parser.add_argument('--date', type=date.fromisoformat,
                        default=datetime.now(timezone(timedelta(hours=3))).date())
    parser.add_argument('--seed', type=int, default=None, help='Optional seed for repeatable output')
    parser.add_argument('--generic-only', action='store_true',
                        help='Omit the reworded, mixed workbook-theme enrichment')
    args = parser.parse_args()
    seed = args.seed if args.seed is not None else random.SystemRandom().randrange(2**63)
    generate(args.output, args.date, seed, mix_clinical=not args.generic_only)
    print('Created 50 sample patients (seed={}): {}'.format(seed, args.output))


if __name__ == '__main__':
    main()
