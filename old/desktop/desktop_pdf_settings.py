"""PDF format settings; appearance follows the selected app accent."""


def pdf_settings(owner):
    from desktop_reports import options_dialog
    owner.ready(False)
    options_dialog(owner, settings_only=True)
