"""Device-local appearance and shared printable accents; clinical severity stays separate."""
import copy
import tkinter as tk
import customtkinter as native
from desktop_protocol import BridgeError, require

from desktop_tokens import ACCENTS, UI_FONT_FAMILY

PALETTES=ACCENTS

SIZES={1.0:'عادي',1.15:'كبير',1.3:'أكبر'}

LIGHT_BACKGROUND='#EDF1F5'
LIGHT_SURFACE='#FFFFFF'
LIGHT_CARD='#FFFFFF'
LIGHT_FIELD='#F4F7FA'
LIGHT_BORDER='#72838B'


def load_demo_appearance():
    """Read cosmetic choices only; call on the IO executor before demo layout."""
    import json
    from desktop_platform import data_directory
    path=data_directory()/'demo-appearance.json'
    try:
        if path.stat().st_size>4096:return {}
        value=json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(value,dict):return {}
        result={key:value[key] for key in ('mode','accent') if key in value}
        validate(dict(dict(mode='System',accent='VANILLA',scale=1.0),**result))
        return result
    except (OSError,ValueError,TypeError,BridgeError):return {}


def save_demo_appearance(value):
    """An isolated cosmetic file never includes demo patients or session settings."""
    import json
    import os
    import tempfile
    from pathlib import Path
    from desktop_platform import data_directory
    validate(value)
    directory=data_directory();directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    temporary=None
    try:
        with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',dir=str(directory),
                                         prefix='.demo-appearance-',delete=False) as stream:
            temporary=Path(stream.name)
            json.dump({key:value[key] for key in ('mode','accent')},stream)
        os.replace(str(temporary),str(directory/'demo-appearance.json'))
    finally:
        if temporary is not None and temporary.exists():temporary.unlink()


def preferences(owner):
    value=dict(mode=owner.settings.get('APPEARANCE') or 'System',accent='VANILLA',scale=1.0,density='عادية')
    if owner.store:value.update(owner.store.state.get('appearance',{}))
    if value.get('scale') not in SIZES:value['scale']=1.0
    if value.get('density') not in {'عادية','مريحة'}:value['density']='عادية'
    return value


def validate(value):
    require(value.get('mode') in {'System','Light','Dark','AMOLED'} and value.get('accent') in PALETTES and
            value.get('scale') in SIZES and value.get('density','عادية') in {'عادية','مريحة'},'إعدادات مظهر غير صالحة')


def set_palette_theme(accent='VANILLA',mode='System'):
    native.set_default_color_theme('blue')
    theme=copy.deepcopy(native.ThemeManager.theme)
    theme['CTkFont']['family']=UI_FONT_FAMILY
    normal=list(PALETTES[accent][1:])
    hover=['#'+''.join('{:02X}'.format(round(int(face[i:i+2],16)*0.8)) for i in (1,3,5)) for face in normal]
    # Replace theme accents only. Explicit badge/error colors are left untouched.
    normal_before=theme['CTkButton']['fg_color']
    hover_before=theme['CTkButton']['hover_color']
    for styles in theme.values():
        for key,color in styles.items():
            if color==normal_before:styles[key]=normal
            elif color==hover_before:styles[key]=hover
    theme['CTkButton']['text_color']=['#FFFFFF','#172125']
    for widget, prop, light in [('CTk','fg_color',LIGHT_BACKGROUND), ('CTkFrame','fg_color',LIGHT_SURFACE),
                                ('CTkFrame','top_fg_color','#E8EEF2')]:
        color=theme[widget][prop]
        dark='#000000' if mode=='AMOLED' else '#171D24' if widget=='CTk' else '#394655' if prop=='top_fg_color' else '#252F3A'
        theme[widget][prop]=[light,dark]
    for name in ('CTkEntry','CTkTextbox','CTkComboBox'):
        styles=theme[name]
        for prop,light in [('fg_color',LIGHT_FIELD),('border_color',LIGHT_BORDER),('text_color','#24352B')]:
            old=styles[prop];styles[prop]=[light,old[1]] if isinstance(old,list) else light
        styles['border_width']=2
    theme['CTkComboBox']['button_color']=[LIGHT_BORDER,theme['CTkComboBox']['button_color'][1]]
    theme['CTkComboBox']['button_hover_color']=['#788D80',theme['CTkComboBox']['button_hover_color'][1]]
    native.ThemeManager.theme=theme


def apply(owner,value):
    validate(value)
    old=copy.deepcopy(native.ThemeManager.theme)
    set_palette_theme(value['accent'],value['mode'])
    theme=native.ThemeManager.theme
    # Keep the generated palette in memory, including in demo mode.
    native.ThemeManager.theme=theme
    def update(widget):
        for name,old_styles in old.items():
            cls=getattr(native,name,None)
            if not isinstance(cls,type) or not isinstance(widget,cls):continue
            for prop,color in theme.get(name,{}).items():
                if old_styles.get(prop)==color:continue
                try:
                    if widget.cget(prop)==old_styles.get(prop):widget.configure(**{prop:color})
                except (ValueError,AttributeError,tk.TclError):pass
            break
        for child in widget.winfo_children():update(child)
    update(owner.root)
    native.set_appearance_mode('Dark' if value['mode']=='AMOLED' else value['mode'])
    # Font sizing scales controls with the text so minimum action targets grow too.
    native.set_widget_scaling(value['scale'])
    if hasattr(owner,'card_density'):owner.card_density.set(value.get('density','عادية'))


def set_density(owner,value):
    previous=preferences(owner).get('density','عادية') if owner.store else 'عادية'
    try:
        owner.ready(False)
        current=preferences(owner);current['density']=value;validate(current)
        require(not owner.store.locked,'الجلسة مقفلة')
        owner.io(lambda:owner.store.commit(appearance=current))
    except Exception:
        owner.card_density.set(previous)
        raise
    owner.card_density.set(value)
    owner.render()


def appearance_dialog(owner):
    import desktop_widgets as ctk
    from desktop_forms import modal,combo,guarded,dialog_actions
    owner.ready(False)
    current=preferences(owner)
    current['density']=owner.card_density.get()
    window=modal(owner,'المظهر والخط','680x560')
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    mode=combo(area,'المظهر',{'System':'حسب النظام','Light':'فاتح','Dark':'داكن','AMOLED':'أسود AMOLED'},current['mode'])
    accent=combo(area,'اللون الأساسي',{key:item[0] for key,item in PALETTES.items()},current['accent'])
    scale=combo(area,'حجم الخط وعناصر الواجهة',SIZES,current['scale'])
    ctk.CTkLabel(area,text='اللون الأساسي مشترك مع عناوين PDF. ألوان التنبيهات مستقلة. مسافات البطاقات ضمن خيارات عرض الجناح.',
        wraplength=560,justify='right',anchor='e').pack(fill='x',padx=10,pady=8)
    def save():
        value=dict(mode=mode(),accent=accent(),scale=scale(),density=current['density']);validate(value)
        require(not owner.store.locked,'الجلسة مقفلة')
        if owner.demo:owner.io(lambda:save_demo_appearance(value))
        owner.io(lambda:owner.store.commit(appearance=value))
        if not window.winfo_exists():return
        apply(owner,value)
        owner.render()
        window.destroy()
    dialog_actions(controls,primary=('تطبيق وحفظ',guarded(save)),cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
