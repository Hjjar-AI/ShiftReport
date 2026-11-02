"""Repeatable lab groups and a dependency-free calendar selector."""
import calendar
from datetime import date
import uuid
import desktop_widgets as ctk
from desktop_widgets import messagebox
from desktop_form_controls import entry, button, section_heading
from desktop_labs import read_groups


def select_date(parent, value, on_change):
    host = parent.winfo_toplevel()
    window = ctk.CTkToplevel(host)
    window.title('تاريخ التحاليل'); window.geometry('560x680')
    window.transient(host); window.grab_set()
    current = date.fromisoformat(value) if value else date.today()
    shown = [current.year, current.month]
    body = ctk.CTkScrollableFrame(window); body.pack(fill='both', expand=True, padx=8, pady=8)
    def finish(value, apply=True):
        if apply:on_change(value)
        window.destroy()
        if host.winfo_exists():host.grab_set()
    def shift(delta):
        number = shown[0]*12 + shown[1]-1 + delta
        year, month = divmod(number, 12)
        if 1 <= year <= 9999:shown[:] = [year, month+1]; render()
    def render():
        for child in body.winfo_children():child.destroy()
        for col in range(7):body.grid_columnconfigure(col, weight=1)
        ctk.CTkButton(body,text='‹',width=48,height=48,command=lambda:shift(-1)).grid(row=0,column=0)
        ctk.CTkLabel(body,text='{}-{:02d}'.format(*shown)).grid(row=0,column=1,columnspan=5)
        ctk.CTkButton(body,text='›',width=48,height=48,command=lambda:shift(1)).grid(row=0,column=6)
        weekdays=['اثن','ثلا','أرب','خمي','جمع','سبت','أحد']
        for col,label in enumerate(weekdays):ctk.CTkLabel(body,text=label).grid(row=1,column=6-col)
        for row,week in enumerate(calendar.monthcalendar(*shown),2):
            for col,day in enumerate(week):
                if day:
                    picked=date(shown[0],shown[1],day).isoformat()
                    ctk.CTkButton(body,text=str(day),width=48,height=48,
                        command=lambda v=picked:finish(v)).grid(row=row,column=6-col,padx=1,pady=1)
    render()
    button(window,'اليوم',lambda:finish(date.today().isoformat()))
    button(window,'مسح التاريخ',lambda:finish(None))
    button(window,'إلغاء',lambda:finish(None,False))
    window.protocol('WM_DELETE_WINDOW',lambda:finish(None,False))


def lab_groups_editor(parent, source):
    groups=read_groups(source)
    controls=[]
    area=ctk.CTkFrame(parent); area.pack(fill='x',padx=6,pady=4)
    section_heading(area,'مجموعات التحاليل')
    rows=ctk.CTkFrame(area); rows.pack(fill='x')
    def read():
        return [dict(id=item['id'], title=item['title'].get(), date=item['date'][0],
                     results=item['results'].get('1.0','end-1c')) for item in controls]
    def change(action):
        groups[:]=read(); action(); render()
    def move(index,delta):
        change(lambda:groups.insert(index+delta,groups.pop(index)))
    def remove(index):
        if messagebox.askyesno('حذف مجموعة تحاليل','حذف هذه المجموعة؟ يُعتمد الحذف عند حفظ المريض.',default='no',parent=parent.winfo_toplevel()):
            change(lambda:groups.pop(index))
    def render():
        controls.clear()
        for child in rows.winfo_children():child.destroy()
        for index,group in enumerate(groups):
            frame=ctk.CTkFrame(rows); frame.pack(fill='x',padx=4,pady=6)
            heading=ctk.CTkFrame(frame,fg_color='transparent');heading.pack(fill='x',padx=4,pady=2)
            ctk.CTkLabel(heading,text='مجموعة تحاليل {}'.format(index+1),anchor='e').pack(side='right',fill='x',expand=True)
            for label,action,enabled in [
                ('↑ أعلى',lambda i=index:move(i,-1),index>0),
                ('↓ أسفل',lambda i=index:move(i,1),index<len(groups)-1),
                ('حذف',lambda i=index:remove(i),True)]:
                control=ctk.CTkButton(heading,text=label,width=48,height=48,command=action,
                    state='normal' if enabled else 'disabled')
                control.pack(side='left',padx=2)

            title=entry(frame,'عنوان المجموعة — اختياري',group['title'])
            chosen=[group['date']]
            def choose(chosen=chosen, frame=frame):
                def selected(value):
                    chosen[0]=value
                    date_button.configure(text='تاريخ التحاليل: '+(value or 'اختياري'))
                # Capture the actual button from this group, not the last loop iteration.
                date_button=next(item['button'] for item in controls if item['date'] is chosen)
                select_date(frame,chosen[0],selected)
            picker=button(frame,'تاريخ التحاليل: '+(chosen[0] or 'اختياري'),choose)
            results=entry(frame,'النتائج',group['results'],True)
            controls.append(dict(id=group['id'],title=title,date=chosen,results=results,button=picker))
    def add():
        change(lambda:groups.append(dict(id=str(uuid.uuid4()),title='',date=None,results='')))
    button(area,'إضافة مجموعة تحاليل',add)
    render()
    return read
