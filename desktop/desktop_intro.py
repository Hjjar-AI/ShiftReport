"""Shared desktop branding and credits, with a five-second skippable intro."""
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
from pathlib import Path
import time
from PIL import Image
import desktop_widgets as ctk

SUPERVISOR='الطبيب محمد علي حجار'
CONTRIBUTORS=('أيهم شيخة','محمد زاهر شقير')
ORIGINAL_AUTHOR='الطبيب الاختصاصي نديم العباس'


@lru_cache(maxsize=1)
def load_emblem():
    # Call from the existing IO executor, or the splash's short-lived worker.
    with Image.open(Path(__file__).resolve().parent/'assets'/'app_emblem.png') as source:
        return source.copy()


def credits(parent,emblem=None):
    ctk.CTkLabel(parent,text='تقرير المناوبة',font=('Arial',24,'bold'),anchor='center').pack(fill='x',padx=12,pady=(12,3))
    ctk.CTkLabel(parent,text='للطب النفسي',anchor='center').pack(fill='x',padx=12,pady=3)
    logo=ctk.CTkLabel(parent,text='شعار شعبة الطب النفسي',fg_color='#FFFFFF',width=158,height=158,corner_radius=16)
    logo.pack(padx=12,pady=10)
    if emblem is not None:set_emblem(logo,emblem)
    for title,names in [('إشراف وتطوير',SUPERVISOR),('مساهمة تقنية · الأطباء','\n'.join(CONTRIBUTORS)),
                        ('فكرة Excel الأولية وبنية التقرير الأساسي',ORIGINAL_AUTHOR)]:
        ctk.CTkLabel(parent,text=title,anchor='center',justify='center',wraplength=480).pack(fill='x',padx=12,pady=(8,1))
        ctk.CTkLabel(parent,text=names,font=('Arial',17,'bold'),anchor='center',justify='center',wraplength=480).pack(fill='x',padx=12,pady=(1,5))
    return logo


def set_emblem(label,emblem):
    picture=ctk.CTkImage(light_image=emblem,dark_image=emblem,size=(142,142))
    label.emblem=picture
    label.configure(text='',image=picture)


def about(owner):
    from desktop_forms import modal,dialog_actions
    window=modal(owner,'حول التطبيق','680x740')
    actions=ctk.CTkFrame(window);actions.pack(side='bottom',fill='x')
    area=ctk.CTkScrollableFrame(window);area.pack(fill='both',expand=True,padx=8,pady=8)
    credits(area,owner.io(load_emblem))
    ctk.CTkLabel(area,text='ShiftReport Desktop · Windows / Linux',anchor='center').pack(fill='x',padx=12,pady=12)
    dialog_actions(actions,cancel=('إغلاق',window.destroy))
    owner.root.wait_window(window)


class LaunchIntro:
    def __init__(self,root,on_ready):
        self.root,self.on_ready=root,on_ready
        self.deadline=time.monotonic()+5
        self.held=False;self.finished=False;self.poll_id=None
        root.title('ShiftReport');root.geometry('900x720');root.minsize(580,430)
        self.frame=ctk.CTkFrame(root);self.frame.pack(fill='both',expand=True)
        actions=ctk.CTkFrame(self.frame,fg_color='transparent');actions.pack(side='bottom',fill='x')
        ctk.CTkButton(actions,text='دخول التطبيق',height=48,command=self.finish).pack(fill='x',padx=16,pady=10)
        area=ctk.CTkScrollableFrame(self.frame);area.pack(fill='both',expand=True,padx=12,pady=12)
        self.logo=credits(area)
        events={'<ButtonPress-1>':self.press,'<ButtonRelease-1>':self.release,
                '<Return>':lambda event:self.finish(),'<Escape>':lambda event:self.finish()}
        self.key_bindings={event:root.bind(event,callback,add='+') for event,callback in events.items()}
        self.timer_id=root.after(5000,self.expire)
        self.executor=ThreadPoolExecutor(max_workers=1)
        self.future=self.executor.submit(load_emblem)
        self.poll_id=root.after(20,self.poll_emblem)
        root.protocol('WM_DELETE_WINDOW',self.close)

    def poll_emblem(self):
        self.poll_id=None
        if self.finished:return
        if self.future.done():
            try:set_emblem(self.logo,self.future.result())
            except Exception:self.logo.configure(text='شعار شعبة الطب النفسي')
        else:self.poll_id=self.root.after(20,self.poll_emblem)

    def press(self,event=None):self.held=True

    def release(self,event=None):
        self.held=False
        self.finish()

    def expire(self):
        if self.finished:return
        self.timer_id=None
        remaining=self.deadline-time.monotonic()
        if remaining>0:self.timer_id=self.root.after(max(1,int(remaining*1000)),self.expire)
        elif not self.held:self.finish()

    def dispose(self):
        self.finished=True
        for key in (self.timer_id,self.poll_id):
            if key is not None:self.root.after_cancel(key)
        for event,key in self.key_bindings.items():self.root.unbind(event,key)
        self.future.cancel();self.executor.shutdown(wait=False)

    def finish(self):
        if self.finished:return
        self.dispose();self.frame.destroy();self.on_ready()

    def close(self):
        if self.finished:return
        self.dispose();self.root.destroy()
