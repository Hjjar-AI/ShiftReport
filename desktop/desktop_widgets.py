"""Presentation-only Arabic adapters for Tk builds without bidi layout.

Values returned to application code remain logical Unicode. Never use visual_text
for a patient model, configuration, a saved draft, a Telegram payload or a PDF.
"""
from __future__ import annotations
import re
import textwrap
import sys
import tkinter as tk
from tkinter import font as tkfont, messagebox as native_messagebox, simpledialog as native_simpledialog
import unicodedata
import arabic_reshaper
from bidi.algorithm import get_display
import customtkinter as native

_VISUAL = sys.platform.startswith('linux')
_SHAPER = arabic_reshaper.ArabicReshaper(configuration={
    'delete_harakat': False, 'shift_harakat_position': True,
})


def configure_rendering(mode='auto'):
    global _VISUAL
    if mode not in {'auto', 'native', 'shaped'}:
        raise ValueError('TEXT_RENDERING must be auto, native or shaped')
    _VISUAL = mode == 'shaped' or mode == 'auto' and sys.platform.startswith('linux')


def visual_text(text):
    text = str(text)
    if not _VISUAL:
        return text
    lines = []
    for line in text.split('\n'):
        if any(unicodedata.bidirectional(c) in {'AL','R'} for c in line):
            # python-bidi 0.4.2 predates Unicode isolates. Suppress their invisible
            # controls in this view only; the original string is never modified.
            visible = line.translate(dict.fromkeys(map(ord, '\u2066\u2067\u2068\u2069')))
            line = get_display(_SHAPER.reshape(visible), base_dir='R')
        lines.append(line)
    return '\n'.join(lines)


def wrapped_text(text, measure, width):
    """Wrap logical paragraphs first, then shape each physical line independently."""
    if not _VISUAL or not width:
        return visual_text(text)
    output = []
    width = max(20, width)
    for paragraph in str(text).split('\n'):
        line = ''
        for token in re.findall(r'\s+|\S+', paragraph):
            if line and measure(visual_text(line+token)) > width:
                output.append(visual_text(line.rstrip()))
                line = ''
            while token and measure(visual_text(token)) > width:
                low, high = 1, len(token)
                while low < high:
                    middle = (low+high+1)//2
                    if measure(visual_text(token[:middle])) <= width:
                        low = middle
                    else:
                        high = middle-1
                output.append(visual_text(token[:low])); token = token[low:]
            line += token
        output.append(visual_text(line.rstrip()))
    return '\n'.join(output)


def _font(kwargs):
    kwargs.setdefault('font', ('DejaVu Sans' if sys.platform.startswith('linux') else 'Arial', 14))
    return kwargs


class CTkLabel(native.CTkLabel):
    def __init__(self, master, text='', textvariable=None, **kwargs):
        self.logical_text = str(text)
        self._source_variable, self._source_trace = textvariable, None
        super().__init__(master, text='', **_font(kwargs))
        if textvariable is not None:
            self.logical_text = textvariable.get()
            self._source_trace = textvariable.trace_add('write', self._variable_changed)
        self._refresh_text()

    def _variable_changed(self, *args):
        self.logical_text = self._source_variable.get()
        self._refresh_text()

    def _refresh_text(self):
        if not hasattr(self, '_label'):
            return
        font = tkfont.Font(root=self, font=self._label.cget('font'))
        width = self._label.cget('wraplength')
        rendered = wrapped_text(self.logical_text, font.measure, float(width) if width else 0)
        super().configure(text=rendered)

    def configure(self, require_redraw=False, **kwargs):
        if 'text' in kwargs:
            self.logical_text = str(kwargs.pop('text'))
        super().configure(require_redraw=require_redraw, **kwargs)
        self._refresh_text()

    def destroy(self):
        if self._source_variable is not None and self._source_trace:
            self._source_variable.trace_remove('write', self._source_trace)
        super().destroy()


class CTkButton(native.CTkButton):
    def __init__(self, master, text='', **kwargs):
        super().__init__(master, text=visual_text(text), **_font(kwargs))

    def configure(self, require_redraw=False, **kwargs):
        if 'text' in kwargs: kwargs['text'] = visual_text(kwargs['text'])
        super().configure(require_redraw=require_redraw, **kwargs)


class CTkCheckBox(native.CTkCheckBox):
    def __init__(self, master, text='', **kwargs):
        super().__init__(master, text=visual_text(text), **_font(kwargs))

    def configure(self, require_redraw=False, **kwargs):
        if 'text' in kwargs: kwargs['text'] = visual_text(kwargs['text'])
        super().configure(require_redraw=require_redraw, **kwargs)


class CTkEntry(native.CTkEntry):
    """Entry content stays logical: passwords, identifiers and cursor edits are untouched."""
    def __init__(self, master, **kwargs):
        if kwargs.get('placeholder_text'):
            kwargs['placeholder_text'] = visual_text(kwargs['placeholder_text'])
        super().__init__(master, **_font(kwargs))


class CTkComboBox(native.CTkComboBox):
    """Read-only choices render visually but get/set/variables/callbacks stay logical."""
    def __init__(self, master, values=None, variable=None, command=None, **kwargs):
        self._logical_values = list(values or [])
        self._logical_command = command
        self._logical_variable, self._logical_trace = variable, None
        self._logical_current = variable.get() if variable is not None else (self._logical_values[0] if self._logical_values else '')
        self._display_map = self._make_map(self._logical_values)
        super().__init__(master, values=list(self._display_map), command=self._selected, **_font(kwargs))
        self.set(self._logical_current)
        if variable is not None:
            self._logical_trace = variable.trace_add('write', self._variable_changed)

    @staticmethod
    def _make_map(values):
        mapping = {visual_text(value): value for value in values}
        if len(mapping) != len(set(values)):
            raise ValueError('Choice labels must remain distinct when rendered')
        return mapping

    def _variable_changed(self, *args):
        self._logical_current = self._logical_variable.get()
        super().set(visual_text(self._logical_current))

    def _selected(self, display_value):
        self.set(self._display_map[display_value])
        if self._logical_command:
            self._logical_command(self._logical_current)

    def get(self):
        return self._logical_current

    def set(self, value):
        self._logical_current = value
        super().set(visual_text(value))
        if self._logical_variable is not None and self._logical_variable.get() != value:
            self._logical_variable.set(value)

    def configure(self, require_redraw=False, **kwargs):
        if 'values' in kwargs:
            self._logical_values = list(kwargs['values'])
            self._display_map = self._make_map(self._logical_values)
            kwargs['values'] = list(self._display_map)
        if 'command' in kwargs:
            self._logical_command = kwargs.pop('command')
        super().configure(require_redraw=require_redraw, **kwargs)

    def destroy(self):
        if self._logical_variable is not None and self._logical_trace:
            self._logical_variable.trace_remove('write', self._logical_trace)
        super().destroy()


def set_readonly_text(widget, logical_text):
    """Render review text at its actual width; clipboard copies the original full text."""
    def render(event=None):
        if not widget.winfo_exists(): return
        position = widget.yview()[0]
        font = tkfont.Font(root=widget, font=widget._textbox.cget('font'))
        text = wrapped_text(logical_text, font.measure, max(80, widget._textbox.winfo_width()-24))
        widget.configure(state='normal', wrap='none')
        widget.delete('1.0','end'); widget.insert('1.0',text)
        widget.tag_config('rtl',justify='right');widget.tag_add('rtl','1.0','end')
        widget.configure(state='disabled');widget.yview_moveto(position)
    def copy_original(event=None):
        widget.clipboard_clear(); widget.clipboard_append(logical_text)
        return 'break'
    widget.bind('<Configure>',render,add=True)
    widget.bind('<<Copy>>',copy_original,add=True)
    widget.bind('<Control-c>',copy_original,add=True)
    widget.after_idle(render)
    return copy_original


def _dialog_text(text):
    if not _VISUAL: return text
    if tk._default_root is None:
        return '\n'.join(visual_text(line) for paragraph in str(text).split('\n')
                         for line in (textwrap.wrap(paragraph,30) or ['']))
    font = tkfont.nametofont('TkDefaultFont')
    return wrapped_text(text,font.measure,220)


class _MessageBoxes:
    def __getattr__(self,name):
        target = getattr(native_messagebox,name)
        def show(title, message, **kwargs):
            return target(title,_dialog_text(message),**kwargs)
        return show


class _SimpleDialogs:
    @staticmethod
    def askstring(title,prompt,**kwargs):
        return native_simpledialog.askstring(title,_dialog_text(prompt),**kwargs)


messagebox = _MessageBoxes()
simpledialog = _SimpleDialogs()


def __getattr__(name):
    return getattr(native,name)


def attach_input_preview(widget, multiline=False):
    """Readable Arabic context beside native logical input; never attach to secrets."""
    preview = CTkLabel(widget.master, text='', anchor='e', justify='right',
                       text_color=('#40566b','#a7bed2'), font=('DejaVu Sans' if sys.platform.startswith('linux') else 'Arial',13))
    pending = [None]
    def refresh():
        pending[0] = None
        if not widget.winfo_exists(): return
        if multiline:
            # A bounded context follows the caret, so long clinical notes do not
            # turn a compact editor into an unbounded duplicate of the document.
            index = widget._textbox.index('insert')
            line = widget.get(index+' linestart',index+' lineend')
            column = int(index.split('.')[1])
            start = max(0,column-180)
            text = ('…' if start else '')+line[start:start+360]+('…' if start+360<len(line) else '')
        else:
            text = widget.get()
        if _VISUAL and any(unicodedata.bidirectional(c) in {'AL','R'} for c in text):
            preview.configure(text='معاينة: '+text,wraplength=max(160,widget.winfo_width()-24))
            try: preview.pack(fill='x',padx=10,pady=(0,4),after=widget)
            except tk.TclError: pass  # Initial insert can precede geometry management.
        else:
            preview.pack_forget()
    def schedule(event=None):
        if pending[0] is None and widget.winfo_exists():
            pending[0] = widget.after_idle(refresh)
    for event in ['<KeyRelease>','<ButtonRelease-1>','<<Paste>>','<<Cut>>','<FocusIn>','<Map>','<Configure>']:
        widget.bind(event,schedule,add=True)
    def cleanup(event=None):
        if pending[0] is not None:
            try:widget.after_cancel(pending[0])
            except tk.TclError:pass
            pending[0]=None
    widget.bind('<Destroy>',cleanup,add=True)
    schedule()
    return preview
