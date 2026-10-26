"""Public report filenames and Telegram titles, separate from PDF interior labels."""
from datetime import date
import re


def report_filename(report_day, supervisor=None, supervisor_name=None, assignments=False,
                    unassigned=False, draft=False, demo=False, extension='.pdf'):
    stem=('Assignments' if assignments else 'Report')+'_'+date.fromisoformat(report_day).strftime('%Y%m%d')
    if unassigned:stem+='_Unassigned'
    elif supervisor is not None:
        name=re.sub(r'[^\w-]+','_',supervisor_name or '',flags=re.UNICODE).strip('_-')[:60] or 'Unnamed'
        stem+='_Supervisor_'+name
    if draft:stem+='_DRAFT'
    if demo:stem+='_DEMO'
    return stem+extension


def report_caption(shift, doctors, report_day, supervisor=None):
    names=[doctors.get(key,{}).get('name','') for key in shift['meta'].get('doctors','').split(',') if key]
    title='تقرير المناوبة: '+report_day+' · أطباء المناوبة: '+' / '.join(names)
    if supervisor is not None:title+='\nمرضى المشرف: '+doctors.get(supervisor,{}).get('name','')
    return title
