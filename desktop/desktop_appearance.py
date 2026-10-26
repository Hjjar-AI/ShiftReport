"""Device-local appearance preferences; independent from PDF and clinical severity."""
import copy
import tkinter as tk
import customtkinter as native
from desktop_protocol import require

PALETTES={
    'VANILLA':('فيروز · Turquoise','#006B72'),
    'SYSTEM':('لازورد · Lapis','#2346B5'),
    'COASTAL':('ورد · Rose','#A01575'),
    'CREAM':('خشخاش · Poppy','#AD3D12')}

LEGACY_ACCENTS={'SAGE':'VANILLA','SUNSET':'CREAM','FUCHSIA':'COASTAL','CHOCOLATE':'SYSTEM'}

SIZES={1.0:'عادي',1.15:'كبير',1.3:'أكبر'}


def preferences(owner):
    value=dict(mode=owner.settings.get('APPEARANCE') or 'Light',accent='VANILLA',scale=1.0,density='عادية')
    value.update(owner.store.state.get('appearance',{}))
    value['accent']=LEGACY_ACCENTS.get(value['accent'],value['accent'])
    return value


def validate(value):
    require(value.get('mode') in {'System','Light','Dark'} and value.get('accent') in PALETTES and
            value.get('scale') in SIZES and value.get('density','عادية') in {'عادية','مضغوطة'},'إعدادات مظهر غير صالحة')


def set_palette_theme(accent='VANILLA'):
    accent=LEGACY_ACCENTS.get(accent,accent)
    native.set_default_color_theme('blue')
    theme=copy.deepcopy(native.ThemeManager.theme)
    normal=PALETTES[accent][1]
    hover='#'+''.join('{:02X}'.format(round(int(normal[i:i+2],16)*0.8)) for i in (1,3,5))
    # Replace theme accents only. Explicit badge/error colors are left untouched.
    normal_before=theme['CTkButton']['fg_color']
    hover_before=theme['CTkButton']['hover_color']
    for styles in theme.values():
        for key,color in styles.items():
            if color==normal_before:styles[key]=[normal,normal]
            elif color==hover_before:styles[key]=[hover,hover]
    for widget, prop, light in [('CTk','fg_color','#F8FAFC'), ('CTkFrame','fg_color','#FFFFFF'),
                                ('CTkFrame','top_fg_color','#F2F5F8')]:
        color=theme[widget][prop]
        theme[widget][prop]=[light,color[1]] if isinstance(color,list) else light
    native.ThemeManager.theme=theme


def apply(owner,value):
    value=dict(value,accent=LEGACY_ACCENTS.get(value['accent'],value['accent']))
    validate(value)
    old=copy.deepcopy(native.ThemeManager.theme)
    set_palette_theme(value['accent'])
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
    native.set_appearance_mode(value['mode'])
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
    from desktop_model import new_patient
    from desktop_patient_card import patient_card
    from types import SimpleNamespace
    owner.ready(False)
    current=preferences(owner)
    current['density']=owner.card_density.get()
    window=modal(owner,'المظهر والخط','960x700')
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True)
    settings=ctk.CTkFrame(area);sample_area=ctk.CTkFrame(area)
    sample=new_patient(owner.client.zone)
    sample.update(externalId='appearance-preview',name='شاي بالنعناع',admitNum='123',birthDate='1990',
        initialDiagnosis='نص تشخيص تجريبي لمعاينة الخط',treatmentPlan='نص تجريبي لخطة العلاج',
        followUp='نص تجريبي للمتابعة',labs='Hb: 12.5 g/dL',
        responsibleResidentId='١',responsibleSupervisorId='٢')
    doctors={'١':dict(name='سنديان',uid='١',clinicalRole='RESIDENT'),
             '٢':dict(name='صنوبر',uid='٢',clinicalRole='SUPERVISOR')}
    preview_owner=SimpleNamespace(content=sample_area,cards=SimpleNamespace(expanded={sample['externalId']},pinned=set()),
        client=SimpleNamespace(doctors=doctors,zone=owner.client.zone))
    def render_preview():
        for child in sample_area.winfo_children():child.destroy()
        ctk.CTkLabel(sample_area,text='معاينة فقط · بيانات تجريبية',anchor='e').pack(fill='x',padx=8,pady=4)
        selected_mode=mode()
        dark=selected_mode=='Dark' or selected_mode=='System' and native.get_appearance_mode()=='Dark'
        style=dict(background='#202832' if dark else '#F5F8FC',foreground='#F1F5FA' if dark else '#18212C',
            muted='#BCCBDD' if dark else '#42556B',accent=PALETTES[accent()][1],
            scale=scale()/current['scale'],density=density())
        patient_card(preview_owner,sample_area,sample,0,1,False,preview_style=style).pack(fill='x',padx=4,pady=4)
    mode=combo(settings,'المظهر',{'System':'حسب النظام','Light':'فاتح','Dark':'داكن'},current['mode'],render_preview)
    accent=combo(settings,'اللون الأساسي',{key:item[0] for key,item in PALETTES.items()},current['accent'],render_preview)
    scale=combo(settings,'حجم الخط وعناصر الواجهة',SIZES,current['scale'],render_preview)
    density=combo(settings,'مسافات البطاقات',{'عادية':'عادية','مضغوطة':'مضغوطة'},current['density'],render_preview)
    ctk.CTkLabel(settings,text='إعدادات محلية لهذا المشروع؛ لا تغيّر ألوان الشارات أو مظهر PDF. تعاين الاختيارات هنا قبل تطبيقها.',
        wraplength=360,justify='right').pack(fill='x',padx=10,pady=8)
    wide=[None];preview_after=[None]
    def layout(event=None):
        if event is not None and event.widget is not window:return
        enough=window.winfo_width()>=900*current['scale']
        if enough!=wide[0]:
            settings.grid_forget();sample_area.grid_forget()
            area.grid_columnconfigure(0,weight=1);area.grid_columnconfigure(1,weight=1 if enough else 0)
            settings.grid(row=0,column=1 if enough else 0,sticky='new',padx=4,pady=4)
            sample_area.grid(row=0 if enough else 1,column=0,sticky='new',padx=4,pady=4)
            wide[0]=enough
        if preview_after[0] is not None:window.after_cancel(preview_after[0])
        def refresh():
            preview_after[0]=None
            if window.winfo_exists():render_preview()
        preview_after[0]=window.after_idle(refresh)
    window.bind('<Configure>',layout,add='+')
    layout()
    def save():
        value=dict(mode=mode(),accent=accent(),scale=scale(),density=density());validate(value)
        require(not owner.store.locked,'الجلسة مقفلة')
        owner.io(lambda:owner.store.commit(appearance=value))
        if not window.winfo_exists():return
        apply(owner,value)
        owner.render()
        window.destroy()
    dialog_actions(controls,primary=('تطبيق وحفظ',guarded(save)),cancel=('إلغاء',window.destroy))
    owner.root.wait_window(window)
