"""Admission-date input normalization; stored/wire dates remain ISO."""
from datetime import date
import re
import unicodedata
from desktop_protocol import require


def admission_date(value):
    value=''.join(str(unicodedata.digit(c)) if c.isdecimal() else c for c in value.strip())
    if not value:return ''
    if re.fullmatch(r'\d{1,2}[\\/]\d{1,2}[\\/]\d{4}',value):
        day,month,year=map(int,re.split(r'[\\/]',value))
        return date(year,month,day).isoformat()
    require(re.fullmatch(r'\d{4}-\d{2}-\d{2}',value),'تاريخ القبول: YYYY-MM-DD أو يوم/شهر/سنة')
    return date.fromisoformat(value).isoformat()
