"""Saved PDF appearance controls shared by settings and report review."""
import tkinter as tk
import desktop_widgets as ctk
from desktop_pdf_content import normalize_options, palette


CHOICES = {
    'color': ('اللون', {'teal':'فيروزي', 'blue':'أزرق', 'green':'أخضر', 'purple':'بنفسجي', 'pastel':'باستيل'}),
    'row_shading': ('تظليل صفوف الجدول', {'auto':'تلقائي حسب الفرق', 'zebra':'صفوف متناوبة', 'none':'دون تظليل'}),
}


def appearance_controls(parent, previous):
    variables, rows = {}, {}
    show_shading = [True]
    preview = tk.Canvas(parent, height=76, highlightthickness=0)
    def read():
        result={key:next(value for value,label in mapping.items() if label==variables[key].get())
                for key,(_,mapping) in CHOICES.items()}
        result.update(rule_weight='medium',rule_contrast='medium',dark=False)
        return result
    def refresh(*args):
        if len(variables)!=len(CHOICES):return
        colors=palette(normalize_options(dict(previous,**read())))
        preview.configure(background=colors['page']);preview.delete('all')
        width=max(100,preview.winfo_width());line_width=colors['rule_width']*2
        for index in range(3):
            y=8+index*21
            shade=colors['page'] if not show_shading[0] or colors['shading']=='none' or index%2==0 else colors['zebra']
            preview.create_rectangle(8,y,width-8,y+21,fill=shade,outline='')
            preview.create_line(8,y+21,width-8,y+21,fill=colors['rule'],width=line_width)
    for key,(label,mapping) in CHOICES.items():
        row=ctk.CTkFrame(parent,fg_color='transparent');row.pack(fill='x');rows[key]=row
        ctk.CTkLabel(row,text=label,anchor='e').pack(fill='x',padx=10,pady=(6,0))
        variable=tk.StringVar(value=mapping[previous[key]]);variables[key]=variable
        ctk.CTkComboBox(row,values=list(mapping.values()),variable=variable,state='readonly',justify='right',height=48,
                        command=lambda value:refresh()).pack(fill='x',padx=10,pady=2)
    preview_label=ctk.CTkLabel(parent,text='معاينة الفواصل · التظليل يخص الجداول وتوزيع الفرق. تبقى البطاقات A5 بهوامش ثابتة.',
                    anchor='e',justify='right',wraplength=560)
    preview_label.pack(fill='x',padx=10,pady=(6,2))
    preview.pack(fill='x',padx=10,pady=4);preview.bind('<Configure>',refresh)
    def set_layout(layout):
        show_shading[0]=layout in {'table','assignments'}
        if layout in {'table','assignments'}:
            if not rows['row_shading'].winfo_manager():rows['row_shading'].pack(fill='x',before=preview_label)
        else:rows['row_shading'].pack_forget()
        refresh()
    read.set_layout=set_layout
    refresh()
    return read


def pdf_settings(owner):
    from desktop_reports import options_dialog
    owner.ready(False)
    options_dialog(owner, settings_only=True)
