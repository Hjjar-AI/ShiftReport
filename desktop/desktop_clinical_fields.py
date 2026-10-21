"""Free text with optional per-field structured tools over the same clinical draft."""
from datetime import date
import tkinter as tk
import desktop_widgets as ctk
from desktop_input import ArabicEntry
from desktop_forms import entry


class ClinicalField:
    def __init__(self,parent,label,value,allow_date=False,today=None):
        self.frame=ctk.CTkFrame(parent,fg_color='transparent');self.frame.pack(fill='x')
        self.general=entry(self.frame,label,value,True)
        self.details=ctk.CTkFrame(self.frame,fg_color='transparent')
        self.mode='GENERAL';self.rows=[];self.draft=None
        self.allow_date=allow_date;self.today=today or date.today()
        self.tools=ctk.CTkButton(self.frame,text='أدوات البنود والترتيب',height=48,
            fg_color='transparent',text_color=('gray25','gray85'),
            command=lambda:self.show('ADVANCED' if self.mode=='GENERAL' else 'GENERAL'))
        self.tools.pack(fill='x',padx=8,pady=2)

    def get(self,*args):
        if self.mode=='GENERAL':return self.general.get('1.0','end-1c')
        values=[widget.get() for widget in self.rows]
        draft=self.draft.get() if self.draft is not None else ''
        return '\n'.join(values+([draft] if draft else []))

    def show(self,mode):
        if mode==self.mode:return
        value=self.get()
        self.mode=mode
        if mode=='GENERAL':
            self.details.pack_forget()
            self.general.delete('1.0','end');self.general.insert('1.0',value)
            self.general.pack(fill='x',padx=10,pady=2)
        else:
            self.general.pack_forget();self.details.pack(fill='x')
            self.render(value.split('\n') if value else [])
        self.tools.configure(text='العودة للكتابة الحرة' if mode=='ADVANCED' else 'أدوات البنود والترتيب')
        self.tools.pack_forget();self.tools.pack(fill='x',padx=8,pady=2)

    def render(self,values):
        for widget in self.details.winfo_children():widget.destroy()
        self.rows=[]
        area=ctk.CTkFrame(self.details,fg_color='transparent');area.pack(fill='x')
        for index,value in enumerate(values):
            line=ctk.CTkFrame(area,fg_color='transparent');line.pack(fill='x',padx=8,pady=2)
            field=ArabicEntry(line,justify='right',height=48);field.insert(0,value)
            field.pack(side='right',fill='x',expand=True);self.rows.append(field)
            for label,action in [('↑',lambda i=index:self.move(i,-1)),('↓',lambda i=index:self.move(i,1)),
                                 ('حذف',lambda i=index:self.remove(i))]:
                ctk.CTkButton(line,text=label,command=action,width=48,height=48).pack(side='left',padx=2)
        self.draft=entry(self.details,'بند جديد (يُحفظ أيضاً عند حفظ المريض)')
        actions=ctk.CTkFrame(self.details,fg_color='transparent');actions.pack(fill='x')
        for label,action in [('إضافة بند',self.add),('إضافة فاصل',lambda:self.add(separator=True))]:
            ctk.CTkButton(actions,text=label,command=action,height=48).pack(side='right',padx=5,pady=3)
        if self.allow_date:
            ctk.CTkButton(actions,text='إضافة تاريخ',command=self.add_date,height=48).pack(side='right',padx=5,pady=3)

    def values(self):
        value=self.get()
        return value.split('\n') if value else []

    def move(self,index,delta):
        values=self.values();target=index+delta
        if 0<=target<len(self.rows):
            values[index],values[target]=values[target],values[index]
            self.render(values)

    def remove(self,index):
        values=self.values();values.pop(index);self.render(values)

    def add(self,separator=False):
        values=self.values()
        if separator:values.append('---')
        self.render(values)

    def add_date(self):
        from desktop_forms import button,guarded
        parent=self.frame.winfo_toplevel()
        window=ctk.CTkToplevel(parent);window.title('تاريخ التحاليل')
        window.transient(parent);window.grab_set();window.geometry('580x260')
        ctk.CTkLabel(window,text='السنة / الشهر / اليوم',height=48).pack(fill='x')
        area=ctk.CTkFrame(window);area.pack(fill='x')
        variables=[]
        for value,lower,upper in [(self.today.year,1900,2200),(self.today.month,1,12),(self.today.day,1,31)]:
            variable=tk.StringVar(value=str(value));variables.append(variable)
            tk.Spinbox(area,from_=lower,to=upper,textvariable=variable,width=6,font=('Arial',16)).pack(side='left',expand=True,padx=8,pady=12)
        def save():
            value=date(*(int(variable.get()) for variable in variables)).isoformat()
            self.render(self.values()+['@date:'+value]);window.destroy()
        button(window,'إضافة التاريخ',guarded(save));button(window,'إلغاء',window.destroy)
        parent.wait_window(window)
        if parent.winfo_exists():parent.grab_set()
