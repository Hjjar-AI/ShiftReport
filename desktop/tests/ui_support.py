"""Headless callback harness, not a substitute for Tk geometry/accessibility checks."""
from contextlib import ExitStack
from unittest.mock import patch


class Variable:
    def __init__(self, value='', **kwargs):self.value=value;self.traces=[]
    def get(self):return self.value
    def set(self,value):
        self.value=value
        for callback in self.traces:callback()
    def trace_add(self,mode,callback):self.traces.append(callback);return callback
    def trace_remove(self,mode,callback):self.traces.remove(callback)


class Widget:
    def __init__(self,master=None,**kwargs):
        self.master=master;self.children=[];self.options=dict(kwargs);self.logical_text=kwargs.get('text','')
        self.value=(kwargs.get('values') or [''])[0];self.content='';self.manager='';self.layout={};self.exists=True
        self.bindings={};self.timers={};self.delays={};self.on_wait=None;self._parent_canvas=self
        if master is not None:master.children.append(self)
    def configure(self,**kwargs):
        self.options.update(kwargs)
        self.logical_text=self.options.get('text','')
    config=configure
    def cget(self,key):return self.options.get(key)
    def pack(self,**kwargs):self.manager='pack';self.layout=dict(kwargs)
    def grid(self,**kwargs):self.manager='grid';self.layout=dict(kwargs)
    def pack_info(self):
        if self.manager!='pack':raise RuntimeError('widget is not packed')
        return dict(self.layout)
    def pack_forget(self):self.manager=''
    grid_remove=grid_forget=pack_forget
    def grid_configure(self,**kwargs):self.layout.update(kwargs)
    def winfo_children(self):return list(self.children)
    def winfo_manager(self):return self.manager
    def winfo_exists(self):return self.exists
    def winfo_width(self):return 1100
    def winfo_height(self):return 760
    def winfo_screenwidth(self):return 1280
    def winfo_screenheight(self):return 900
    def winfo_toplevel(self):return self if self.master is None else self.master.winfo_toplevel()
    def get(self,*args):
        variable=self.options.get('variable')
        return variable.get() if variable is not None else self.content if args else self.value
    def set(self,value):
        self.value=value
        if self.options.get('variable') is not None:self.options['variable'].set(value)
    def insert(self,index,value):self.content+=value;self.value=self.content
    def delete(self,*args):self.content='';self.value=''
    def invoke(self):
        if self.options.get('state')!='disabled':return self.options['command']()
    def bind(self,event,callback,**kwargs):self.bindings[event]=callback
    bind_all=bind
    def protocol(self,event,callback):self.bindings[event]=callback
    def after(self,delay,callback):
        key=str(len(self.timers));self.timers[key]=callback;self.delays[key]=delay;return key
    def after_cancel(self,key):self.timers.pop(key,None);self.delays.pop(key,None)
    def after_idle(self,callback):callback();return 'idle'
    def wait_window(self,window=None):
        if self.on_wait:self.on_wait(window)
    def yview(self):return (0,1)
    def yview_moveto(self,value):pass
    def destroy(self):
        for child in list(self.children):child.destroy()
        self.exists=False
        if self.master and self in self.master.children:self.master.children.remove(self)
    def withdraw(self):self.manager=''
    def deiconify(self):self.manager='window'
    def register(self,callback):return callback
    def __getattr__(self,name):
        if name in {'title','geometry','minsize','transient','grab_set','lift','focus_set','update','update_idletasks',
                    'grid_columnconfigure','place','add_command','tk_popup','grab_release','tag_config','tag_add','reset_undo','unbind'}:
            return lambda *args,**kwargs:None
        raise AttributeError(name)


class Panel(Widget):
    def __init__(self,master,title='',expanded=False,**kwargs):
        super().__init__(master,**kwargs);self.content=Widget(self);self.expanded=expanded;self.title=title
        self.toggle_button=Widget(kwargs.get('toggle_parent') or self,text=title)
    def refresh(self,title=None):
        if title is not None:self.title=title


class Pages(Widget):
    def __init__(self,master):super().__init__(master);self.pages={};self.selected=None;self.on_change=None
    def add(self,title,label=None):
        self.pages[title]=Widget(self)
        if self.selected is None:self.selected=title
    def tab(self,title):return self.pages[title]
    def get(self):return self.selected


def descendants(widget):
    yield widget
    for child in widget.winfo_children():yield from descendants(child)


def find(widget,text):
    return next(child for child in descendants(widget) if child.cget('text')==text)


def toolkit():
    import desktop_widgets as ctk
    import desktop_input
    import desktop_form_controls
    import desktop_layout
    import desktop_sections  # Load native class bases before replacing toolkit widgets.
    stack=ExitStack()
    for name in ('CTkFrame','CTkLabel','CTkButton','CTkComboBox','CTkCheckBox','CTkScrollableFrame','CTkToplevel','CTkTextbox','CTkEntry'):
        stack.enter_context(patch.object(ctk,name,Widget))
    for module,names in ((desktop_input,('ArabicEntry','ArabicTextbox')),
                         (desktop_form_controls,('ArabicEntry','ArabicTextbox')),
                         (desktop_layout,('ArabicEntry',))):
        for name in names:stack.enter_context(patch.object(module,name,Widget))
    stack.enter_context(patch('tkinter.StringVar',Variable))
    stack.enter_context(patch('tkinter.BooleanVar',Variable))
    stack.enter_context(patch('tkinter.Menu',Widget))
    stack.enter_context(patch('desktop_shell_layout.apply_appearance'))
    stack.enter_context(patch('desktop_shell_layout.debounce',lambda widget,delay,callback:callback))
    stack.enter_context(patch('desktop_layout.debounce',lambda widget,delay,callback:callback))
    stack.enter_context(patch('desktop_shell_layout.Disclosure',Panel))
    stack.enter_context(patch('desktop_layout.Disclosure',Panel))
    stack.enter_context(patch('desktop_sections.SectionPages',Pages))
    def readonly(widget,text):widget.content=text;return lambda:None
    stack.enter_context(patch('desktop_widgets.set_readonly_text',readonly))
    stack.enter_context(patch('desktop_report_review.set_readonly_text',readonly))
    return stack
