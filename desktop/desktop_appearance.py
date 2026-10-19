"""Device-local appearance preferences; independent from PDF and clinical severity."""
import copy
import tkinter as tk
import customtkinter as native
from desktop_protocol import require

PALETTES={
    'SYSTEM':('أطلس · Atlas','#174EA6'), 'SAGE':('غابة · Forest','#1C633B'),
    'COASTAL':('جمشت · Amethyst','#602D91'), 'SUNSET':('كهرمان · Amber','#934500'),
    'FUCHSIA':('كرز · Cherry','#A1264A'), 'VANILLA':('فيروز · Turquoise','#006B72'),
    'CHOCOLATE':('نحاس · Copper','#844328'), 'CREAM':('زيتون · Olive','#526119')}
SIZES={1.0:'عادي',1.15:'كبير',1.3:'أكبر'}


def preferences(owner):
    value=dict(mode=owner.settings.get('APPEARANCE') or 'System',accent='SYSTEM',scale=1.0)
    value.update(owner.store.state.get('appearance',{}))
    return value


def validate(value):
    require(value.get('mode') in {'System','Light','Dark'} and value.get('accent') in PALETTES and
            value.get('scale') in SIZES,'إعدادات مظهر غير صالحة')


def apply(owner,value):
    validate(value)
    old=copy.deepcopy(native.ThemeManager.theme)
    native.set_default_color_theme('blue')
    theme=copy.deepcopy(native.ThemeManager.theme)
    normal=PALETTES[value['accent']][1]
    hover='#'+''.join('{:02X}'.format(round(int(normal[i:i+2],16)*0.8)) for i in (1,3,5))
    # Replace theme accents only. Explicit badge/error colors are left untouched.
    normal_before=theme['CTkButton']['fg_color']
    hover_before=theme['CTkButton']['hover_color']
    for styles in theme.values():
        for key,color in styles.items():
            if color==normal_before:styles[key]=[normal,normal]
            elif color==hover_before:styles[key]=[hover,hover]
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
    native.set_appearance_mode(value['mode'])
    # Font sizing scales controls with the text so minimum action targets grow too.
    native.set_widget_scaling(value['scale'])


def appearance_dialog(owner):
    import desktop_widgets as ctk
    from desktop_forms import modal,combo,button,guarded
    owner.ready(False)
    current=preferences(owner)
    window=modal(owner,'المظهر والخط','660x540')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    mode=combo(area,'المظهر',{'System':'حسب النظام','Light':'فاتح','Dark':'داكن'},current['mode'])
    accent=combo(area,'اللون الأساسي',{key:item[0] for key,item in PALETTES.items()},current['accent'])
    scale=combo(area,'حجم الخط وعناصر الواجهة',SIZES,current['scale'])
    ctk.CTkLabel(area,text='إعدادات محلية لهذا المشروع؛ لا تغيّر ألوان الشارات أو مظهر PDF.',wraplength=480,justify='right').pack(fill='x',padx=10,pady=8)
    controls=ctk.CTkFrame(window);controls.pack(fill='x')
    def save():
        value=dict(mode=mode(),accent=accent(),scale=scale());validate(value)
        require(not owner.store.locked,'الجلسة مقفلة')
        owner.io(lambda:owner.store.commit(appearance=value))
        if not window.winfo_exists():return
        apply(owner,value)
        window.destroy()
    button(controls,'تطبيق وحفظ',guarded(save));button(controls,'إلغاء',window.destroy)
    owner.root.wait_window(window)
