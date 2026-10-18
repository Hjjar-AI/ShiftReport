"""Arabic editors: logical Unicode model, shaped display and mapped caret/selection.

Only the presentation buffer contains Arabic presentation forms. Clipboard,
StringVars and get() always use the original logical text. Passwords use native
masked entries. Native-rendering mode and Windows keep their existing controls.
"""
from __future__ import annotations
from collections import deque
import re
import tkinter as tk
from tkinter import font as tkfont
import unicodedata
import arabic_reshaper
from bidi import algorithm as bidi
import customtkinter as native
import desktop_widgets as widgets

_SHAPER = arabic_reshaper.ArabicReshaper(configuration={
    'support_ligatures': False, 'delete_harakat': False,
    'shift_harakat_position': False, 'delete_tatweel': False,
})


def display_glyphs(text, offset=0):
    """Return visual glyphs carrying their logical source offset and direction."""
    source = [(char, offset+i) for i, char in enumerate(text)
              if unicodedata.category(char) != 'Cf']
    clean = ''.join(char for char, _ in source)
    shaped = _SHAPER.reshape(clean)
    # Never lose model characters when a shaper encounters an unusual sequence.
    if len(shaped) != len(source):
        shaped = clean
    storage = bidi.get_empty_storage()
    storage['base_level'] = bidi.get_base_level(clean)
    storage['base_dir'] = ('L', 'R')[storage['base_level']]
    bidi.get_embedding_levels(shaped, storage)
    for char, (_, index) in zip(storage['chars'], source):
        char['source'] = index
    bidi.explicit_embed_and_overrides(storage)
    bidi.resolve_weak_types(storage)
    bidi.resolve_neutral_types(storage, False)
    bidi.resolve_implicit_levels(storage, False)
    bidi.reorder_resolved_levels(storage, False)
    bidi.apply_mirroring(storage, False)
    return [(char['ch'], char['source'], bool(char['level'] % 2))
            for char in storage['chars']]


def visual_lines(text, measure, width, multiline=True):
    """Wrap logical text before shaping; keep every source offset unchanged."""
    rows, start = [], 0
    paragraphs = text.split('\n') if multiline else [text]
    for paragraph in paragraphs:
        current, first = '', start
        for match in re.finditer(r'\s+|\S+', paragraph):
            token = match.group()
            if multiline and current and measure(''.join(g[0] for g in display_glyphs(current+token))) > width:
                rows.append((first, first+len(current), display_glyphs(current, first)))
                first += len(current)
                current = ''
            while multiline and len(token) > 1 and measure(''.join(g[0] for g in display_glyphs(token))) > width:
                low, high = 1, len(token)
                while low < high:
                    middle = (low+high+1)//2
                    if measure(''.join(g[0] for g in display_glyphs(token[:middle]))) <= width:
                        low = middle
                    else:
                        high = middle-1
                # Keep combining marks with their base character.
                while low < len(token) and unicodedata.combining(token[low]):
                    low += 1
                rows.append((first, first+low, display_glyphs(token[:low], first)))
                first += low
                token = token[low:]
            current += token
        rows.append((first, first+len(current), display_glyphs(current, first)))
        start += len(paragraph)+1
    return rows


class LogicalEditor:
    def __init__(self, widget, multiline, changed=None, validate=None):
        self.widget, self.multiline, self.changed = widget, multiline, changed
        self.validate = validate
        self.text, self.cursor, self.anchor = '', 0, None
        self.undo, self.redo = deque(maxlen=80), deque(maxlen=80)
        self.locations, self.glyphs, self.view_cursor = [(0, 0)], [], 0
        self.pending = None
        widget.configure(wrap='none', undo=False, exportselection=False)
        widget.tag_configure('logical_rtl', justify='right')
        widget.bind('<KeyPress>', self.key)
        for event, action in [('<<Copy>>', self.copy), ('<<Cut>>', self.cut),
                              ('<<Paste>>', self.paste), ('<<PasteSelection>>', self.paste_primary),
                              ('<<SelectAll>>', self.select_all), ('<<Undo>>', self.undo_edit),
                              ('<<Redo>>', self.redo_edit)]:
            widget.bind(event, action)
        widget.bind('<Button-1>', self.click)
        widget.bind('<B1-Motion>', self.drag)
        widget.bind('<Double-Button-1>', self.select_word)
        widget.bind('<Triple-Button-1>', self.select_all)
        widget.bind('<Configure>', self.schedule)
        widget.bind('<Destroy>', self.destroy)
        self.render()

    def destroy(self, event=None):
        if self.pending is not None:
            self.widget.after_cancel(self.pending)
            self.pending = None

    def schedule(self, event=None):
        if self.pending is None:
            self.pending = self.widget.after_idle(self.render)

    def position(self, index):
        if isinstance(index, int) or str(index).isdigit():
            return max(0, min(len(self.text), int(index)))
        if index in ('end', 'end-1c'):
            return len(self.text)
        if index == 'insert':
            return self.cursor
        if index in ('sel.first', 'sel.last'):
            bounds = self.selection()
            if bounds is None:
                raise tk.TclError('No selection')
            return bounds[index == 'sel.last']
        match = re.fullmatch(r'(\d+)\.(\d+)', str(index))
        if match:
            lines = self.text.split('\n')
            row = min(max(0, int(match.group(1))-1), len(lines)-1)
            return sum(len(line)+1 for line in lines[:row])+min(int(match.group(2)), len(lines[row]))
        raise ValueError('Unsupported logical editor index')

    def selection(self):
        return tuple(sorted((self.cursor, self.anchor))) if self.anchor is not None and self.anchor != self.cursor else None

    def snapshot(self):
        return self.text, self.cursor, self.anchor

    def replace(self, first, last, value):
        if self.widget.cget('state') == 'disabled':
            return
        value = str(value).replace('\r\n', '\n').replace('\r', '\n')
        if not self.multiline:
            value = value.replace('\n', ' ')
        proposed = self.text[:first]+value+self.text[last:]
        if self.validate and not self.validate(proposed):
            self.widget.bell()
            return
        self.undo.append(self.snapshot()); self.redo.clear()
        self.text = proposed
        self.cursor, self.anchor = first+len(value), None
        self.render()
        if self.changed:
            self.changed(self.text)

    def input(self, value):
        first, last = self.selection() or (self.cursor, self.cursor)
        self.replace(first, last, value)

    def render(self):
        self.pending = None
        widget = self.widget
        if not widget.winfo_exists():
            return
        font = tkfont.Font(root=widget, font=widget.cget('font'))
        width = max(40, widget.winfo_width()-16)
        rows = visual_lines(self.text, font.measure, width, self.multiline)
        self.locations, self.glyphs = [], []
        displays, offset = [], 0
        for first, last, glyphs in rows:
            displays.append(''.join(glyph[0] for glyph in glyphs))
            if not glyphs:
                self.locations.append((first, offset))
            for number, (char, source, rtl) in enumerate(glyphs):
                self.locations.extend([(source+int(rtl), offset+number),
                                       (source+int(not rtl), offset+number+1)])
                self.glyphs.append((offset+number, source))
            offset += len(glyphs)+1
        scroll = widget.yview()[0]
        state = widget.cget('state')
        widget.configure(state='normal')
        widget.delete('1.0', 'end'); widget.insert('1.0', '\n'.join(displays))
        widget.tag_add('logical_rtl', '1.0', 'end')
        self.show_cursor()
        widget.yview_moveto(scroll)
        widget.see('insert')
        widget.configure(state=state)

    def show_cursor(self):
        choices = [visual for logical, visual in self.locations if logical == self.cursor]
        if not choices:
            closest = min(self.locations, key=lambda pair: abs(pair[0]-self.cursor))[0]
            choices = [visual for logical, visual in self.locations if logical == closest]
        self.view_cursor = min(choices, key=lambda index: abs(index-self.view_cursor))
        self.widget.mark_set('insert', '1.0+{}c'.format(self.view_cursor))
        self.widget.tag_remove('sel', '1.0', 'end')
        bounds = self.selection()
        if bounds:
            for visual, source in self.glyphs:
                if bounds[0] <= source < bounds[1]:
                    self.widget.tag_add('sel', '1.0+{}c'.format(visual), '1.0+{}c'.format(visual+1))

    def move(self, index, extend=False, visual=None):
        if extend and self.anchor is None:
            self.anchor = self.cursor
        elif not extend:
            self.anchor = None
        self.cursor = max(0, min(len(self.text), index))
        if visual is not None:
            self.view_cursor = visual
        self.show_cursor(); self.widget.see('insert')

    def at(self, index, right=False):
        # Tk's count(chars) counts UTF-16 units on some builds, while +Nc and
        # Python iteration step whole characters. Decode the prefix for mapping.
        visual = len(self.widget.get('1.0', index))+int(right)
        candidates = [(logical, where) for logical, where in self.locations if where == visual]
        return (candidates[-1] if right else candidates[0]) if candidates else min(self.locations, key=lambda pair: abs(pair[1]-visual))

    def pointer(self, event):
        index = self.widget.index('@{},{}'.format(event.x, event.y))
        box = self.widget.bbox(index)
        return self.at(index, bool(box and event.x >= box[0]+box[2]/2))

    def click(self, event):
        self.widget.focus_set()
        logical, visual = self.pointer(event)
        self.move(logical, bool(event.state & 1), visual)
        return 'break'

    def drag(self, event):
        logical, visual = self.pointer(event)
        self.move(logical, True, visual)
        return 'break'

    def select_word(self, event):
        logical, _ = self.pointer(event)
        first, last = logical, logical
        while first and not self.text[first-1].isspace(): first -= 1
        while last < len(self.text) and not self.text[last].isspace(): last += 1
        self.anchor = first; self.move(last, True)
        return 'break'

    def select_all(self, event=None):
        self.anchor = 0; self.move(len(self.text), True)
        return 'break'

    def copy(self, event=None):
        bounds = self.selection()
        if bounds:
            self.widget.clipboard_clear(); self.widget.clipboard_append(self.text[bounds[0]:bounds[1]])
        return 'break'

    def cut(self, event=None):
        bounds = self.selection()
        self.copy()
        if bounds: self.replace(*bounds, '')
        return 'break'

    def paste(self, event=None):
        try: self.input(self.widget.clipboard_get())
        except tk.TclError: pass
        return 'break'

    def paste_primary(self, event=None):
        try: self.input(self.widget.selection_get(selection='PRIMARY'))
        except tk.TclError: pass
        return 'break'

    def history(self, source, target):
        if source and self.widget.cget('state') != 'disabled':
            target.append(self.snapshot())
            self.text, self.cursor, self.anchor = source.pop()
            self.render()
            if self.changed: self.changed(self.text)
        return 'break'

    def undo_edit(self, event=None): return self.history(self.undo, self.redo)
    def redo_edit(self, event=None): return self.history(self.redo, self.undo)

    def key(self, event):
        key, control, shift = event.keysym, bool(event.state & 4), bool(event.state & 1)
        if control:
            action = {'a':self.select_all, 'c':self.copy, 'x':self.cut, 'v':self.paste,
                      'z':self.redo_edit if shift else self.undo_edit, 'y':self.redo_edit}.get(key.lower())
            if action: return action()
        if key in ('Left', 'Right', 'Up', 'Down', 'Home', 'End'):
            if control and key in ('Home', 'End'):
                self.move(0 if key == 'Home' else len(self.text), shift)
            else:
                target = self.widget.index('insert {}{}'.format('-' if key in ('Left','Up') else '+',
                    '1c' if key in ('Left','Right') else '1l')) if key in ('Left','Right','Up','Down') else self.widget.index('insert '+('linestart' if key == 'Home' else 'lineend'))
                logical, visual = self.at(target)
                self.move(logical, shift, visual)
            return 'break'
        if key in ('BackSpace', 'Delete'):
            bounds = self.selection()
            if bounds is None:
                first, last = self.cursor, self.cursor
                if key == 'BackSpace' and first:
                    first -= 1
                    while first and unicodedata.combining(self.text[first]): first -= 1
                elif key == 'Delete' and last < len(self.text):
                    last += 1
                    while last < len(self.text) and unicodedata.combining(self.text[last]): last += 1
                bounds = first, last
            if bounds[0] != bounds[1]: self.replace(*bounds, '')
            return 'break'
        if key in ('Return','KP_Enter'):
            if self.multiline: self.input('\n')
            return 'break'
        if key in ('Tab','ISO_Left_Tab'):
            (self.widget.tk_focusPrev() if shift else self.widget.tk_focusNext()).focus_set()
            return 'break'
        if event.char and not control and not event.state & 8 and ord(event.char[0]) >= 32:
            self.input(event.char)
            return 'break'
        return 'break'


class _LogicalTextbox(native.CTkTextbox):
    def __init__(self, master, multiline=True, **kwargs):
        self._editor = None
        self._validation_mode, self._validation_command = 'none', None
        self._variable = kwargs.pop('textvariable', None)
        self._trace = None
        kwargs.pop('justify', None); kwargs.pop('placeholder_text', None)
        kwargs.setdefault('font', ('DejaVu Sans', 15))
        kwargs['wrap'] = 'none'
        kwargs['activate_scrollbars'] = multiline
        super().__init__(master, **kwargs)
        self._editor = LogicalEditor(self._textbox, multiline, self._changed, self._valid)
        if self._variable is not None:
            self._editor.input(self._variable.get())
            self.reset_undo()
            self._trace = self._variable.trace_add('write', self._variable_changed)

    def _changed(self, text):
        if self._variable is not None and self._variable.get() != text:
            self._variable.set(text)

    def _valid(self, proposed):
        if self._validation_mode == 'none' or self._validation_command is None:
            return True
        command, *arguments = self._validation_command
        arguments = [proposed if argument == '%P' else argument for argument in arguments]
        return self.tk.getboolean(self.tk.call(command, *arguments))

    def configure(self, require_redraw=False, **kwargs):
        if 'validate' in kwargs:
            self._validation_mode = kwargs.pop('validate')
        if 'validatecommand' in kwargs:
            self._validation_command = kwargs.pop('validatecommand')
        super().configure(require_redraw=require_redraw, **kwargs)

    def _variable_changed(self, *args):
        if self._variable.get() != self._editor.text:
            self._editor.replace(0, len(self._editor.text), self._variable.get())

    def get(self, index1=None, index2=None):
        if index1 is None: return self._editor.text
        first = self._editor.position(index1)
        last = self._editor.position(index2) if index2 is not None else first+1
        return self._editor.text[first:last]+ ('\n' if index2 == 'end' else '')

    def insert(self, index, text, tags=None):
        first = self._editor.position(index)
        self._editor.replace(first, first, text)

    def delete(self, index1, index2=None):
        first = self._editor.position(index1)
        last = self._editor.position(index2) if index2 is not None else min(len(self._editor.text), first+1)
        self._editor.replace(first, last, '')

    def reset_undo(self):
        self._editor.undo.clear(); self._editor.redo.clear()

    def destroy(self):
        if self._variable is not None and self._trace:
            self._variable.trace_remove('write', self._trace)
        super().destroy()


def ArabicEntry(master, **kwargs):
    if not widgets._VISUAL or kwargs.get('show'):
        return widgets.CTkEntry(master, **kwargs)
    kwargs.setdefault('height', 48)
    return _LogicalTextbox(master, multiline=False, **kwargs)


def ArabicTextbox(master, **kwargs):
    return _LogicalTextbox(master, **kwargs) if widgets._VISUAL else native.CTkTextbox(master, **kwargs)
