"""Stable public form API; implementations are separated by UI responsibility.

The patient editor owns one draft/autosave lifecycle; controls/pickers never save
patients independently.
"""
from desktop_form_controls import button, dialog_actions, section_heading, destination_row, discard_guard, reveal_field, entry, combo, modal, guarded
from desktop_doctor_picker import doctor_picker
from desktop_patient_editor import edit_patient
