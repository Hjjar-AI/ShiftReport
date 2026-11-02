"""Small Tk scheduling helpers; pending callbacks belong to their widget lifetime."""
import tkinter as tk


def debounce(widget, milliseconds, callback):
    pending = [None]

    def cancel():
        if pending[0] is not None:
            widget.after_cancel(pending[0])
            pending[0] = None

    def schedule(*args):
        cancel()

        def invoke():
            pending[0] = None
            callback(*args)

        pending[0] = widget.after(milliseconds, invoke)

    def destroyed(event):
        if event.widget is widget:
            cancel()

    # CTkFrame.bind redirects to its canvas; use the owning widget's lifecycle.
    tk.Misc.bind(widget, '<Destroy>', destroyed, add='+')
    return schedule
