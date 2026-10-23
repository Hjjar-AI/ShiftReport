"""Desktop presentation roles; touch targets and clinical meaning stay independent."""
from enum import Enum

SPACE = dict(xxs=1, xs=2, s=4, half_step=6, m=8, l=12, xl=16, xxl=24)
TARGET = 48
WRAP = dict(narrow=440, dialog=560, page=650, wide=820)
FONT_ROLES = dict(caption=13, body=14, title=16, headline=18, display=23)
CLINICAL_URGENT = ('#B3261E', '#FFB4AB')
CLINICAL_WARNING = ('#8A5100', '#FFC45C')
CLINICAL_PENDING = ('#6750A4', '#D0BCFF')
STATUS_EDGE = 4
WARNING_CONTAINER = ('#FFF3CD', '#493C19')


class ButtonStyle(Enum):
    PRIMARY = 'primary'
    OUTLINED = 'outlined'
    TEXT = 'text'
    DESTRUCTIVE = 'destructive'


def button_options(style=ButtonStyle.PRIMARY):
    """Return fresh options so per-widget overrides cannot mutate another control."""
    if style in (ButtonStyle.OUTLINED, ButtonStyle.TEXT):
        return dict(fg_color='transparent', text_color=('gray20', 'gray90'),
                    border_width=1 if style == ButtonStyle.OUTLINED else 0)
    if style == ButtonStyle.DESTRUCTIVE:
        return dict(fg_color=('#B3261E', '#93000A'), text_color=('white', '#FFDAD6'),
                    hover_color=('#8C1D18', '#690005'))
    return {}
