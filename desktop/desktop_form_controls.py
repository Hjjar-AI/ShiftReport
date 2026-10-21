"""Form controls; shared draft and callbacks remain caller-owned."""
from __future__ import annotations
import tkinter as tk
from desktop_widgets import messagebox
from desktop_input import ArabicEntry, ArabicTextbox
import desktop_widgets as ctk
from desktop_protocol import BridgeError


def button(parent, text, command, **kwargs):
    widget = ctk.CTkButton(parent, text=text, command=command, height=48, **kwargs)
    widget.pack(fill='x', padx=8, pady=4)
    return widget


def dialog_actions(parent, primary=None, cancel=None, secondary=()):
    """Fixed dialog footer: primary on the right, cancel on the left, extras above.

    Long labels use a full row; callbacks retain the caller's validation/review.
    """
    widgets={}
    quiet=dict(fg_color='transparent',border_width=1,text_color=('gray20','gray90'))
    for index,(label,command) in enumerate(secondary):
        widget=ctk.CTkButton(parent,text=label,command=command,height=48,**quiet)
        widget.grid(row=index,column=0,columnspan=2,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    row=len(secondary)
    stacked=bool(primary and len(primary[0])>28)
    parent.grid_columnconfigure(0,weight=1,uniform='dialog-actions')
    parent.grid_columnconfigure(1,weight=1,uniform='dialog-actions')
    if primary:
        label,command=primary
        widget=ctk.CTkButton(parent,text=label,command=command,height=48)
        widget.grid(row=row,column=0 if stacked else 1,columnspan=2 if stacked else 1,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    if cancel:
        label,command=cancel
        widget=ctk.CTkButton(parent,text=label,command=command,height=48,**quiet)
        widget.grid(row=row+int(stacked),column=0,sticky='ew',padx=8,pady=4)
        widgets[label]=widget
    return widgets


def section_heading(parent, title, summary=None):
    ctk.CTkLabel(parent,text=title,font=('Arial',16,'bold'),anchor='e').pack(fill='x',padx=10,pady=(10,3))
    if summary:
        ctk.CTkLabel(parent,text=summary,anchor='e',justify='right',wraplength=560).pack(fill='x',padx=10,pady=2)


def destination_row(parent, title, summary, command):
    return button(parent,title+'  ‹\n'+summary,command,anchor='e',fg_color='transparent',
                  border_width=1,text_color=('gray20','gray90'))


def discard_guard(window, dirty, message='لم تُحفظ التغييرات. هل تريد الخروج دون حفظ؟'):
    def close():
        if not dirty() or messagebox.askyesno('تجاهل التغييرات؟',message,default='no',parent=window):window.destroy()
    window.protocol('WM_DELETE_WINDOW',close)
    window.bind('<Escape>',lambda event:close())
    return close


def reveal_field(widget):
    """Keep keyboard-focused entries visible in the nearest existing scroll frame."""
    if not widget.winfo_exists():return
    container=widget.master
    while container is not None:
        canvas=getattr(container,'_parent_canvas',None)
        if canvas is not None:
            bounds=canvas.bbox('all')
            if not bounds:return
            top=widget.winfo_rooty()-canvas.winfo_rooty()
            bottom=top+widget.winfo_height()
            delta=top-8 if top<8 else bottom-canvas.winfo_height()+8 if bottom>canvas.winfo_height()-8 else 0
            if delta:
                height=max(1,bounds[3]-bounds[1])
                canvas.yview_moveto(max(0,min(1,(canvas.canvasy(0)+delta-bounds[1])/height)))
            return
        container=getattr(container,'master',None)


def entry(parent, label, value='', multiline=False):
    ctk.CTkLabel(parent, text=label, anchor='e').pack(fill='x', padx=10, pady=(6, 0))
    if multiline:
        widget = ArabicTextbox(parent, height=115, wrap='word', font=('Arial', 15))
        widget.insert('1.0', value)
        widget.tag_config('rtl', justify='right'); widget.tag_add('rtl', '1.0', 'end')
        widget.bind('<KeyRelease>', lambda event: widget.tag_add('rtl', '1.0', 'end'))
    else:
        widget = ArabicEntry(parent, justify='right', height=48)
        widget.insert(0, value)
    if hasattr(widget, 'reset_undo'):
        widget.reset_undo()
    widget.pack(fill='x', padx=10, pady=2)
    widget.bind('<FocusIn>',lambda event:widget.after_idle(lambda:reveal_field(widget)),add='+')
    return widget


def combo(parent, label, mapping, value, on_change=None):
    ctk.CTkLabel(parent, text=label, anchor='e').pack(fill='x', padx=10, pady=(6, 0))
    # Labels include stable doctor IDs so duplicate names remain distinguishable.
    mapping = dict(mapping)
    if value not in mapping:
        mapping[value] = 'قيمة محفوظة [' + str(value) + ']'
    variable = tk.StringVar(value=mapping[value])
    widget = ctk.CTkComboBox(parent, values=list(mapping.values()), variable=variable, state='readonly', justify='right', height=48,
                           command=(lambda value:on_change()) if on_change else None)
    widget.pack(fill='x', padx=10, pady=2)
    return lambda: next(key for key, label in mapping.items() if label == variable.get())


def modal(owner, title, geometry='820x680'):
    window = ctk.CTkToplevel(owner.root)
    width,height=(int(value) for value in geometry.split('x'))
    width=min(width,max(580,owner.root.winfo_screenwidth()-60))
    height=min(height,max(430,owner.root.winfo_screenheight()-100))
    if not hasattr(owner,'dialog_sizes'):owner.dialog_sizes={}
    saved=owner.dialog_sizes.get(title)
    if saved:
        width=min(saved[0],max(580,owner.root.winfo_screenwidth()-60))
        height=min(saved[1],max(430,owner.root.winfo_screenheight()-100))
    window.title(title); window.geometry('{}x{}'.format(width,height)); window.minsize(580,430)
    def remember_size(event):
        if event.widget is window and event.width>=580 and event.height>=430:
            owner.dialog_sizes[title]=(event.width,event.height)
    window.bind('<Configure>',remember_size,add='+')
    window.transient(owner.root); window.grab_set()
    return window


def guarded(action):
    def wrapped():
        try:
            action()
        except BridgeError as error:
            messagebox.showerror('ShiftReport', str(error))
        except (ValueError, KeyError, TypeError):
            messagebox.showerror('ShiftReport', 'تحقق من القيم والتواريخ المطلوبة')
    return wrapped

