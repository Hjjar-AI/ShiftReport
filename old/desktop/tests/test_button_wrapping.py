"""Exercise wrapping/height calculations without starting a graphical Tk session."""
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import desktop_widgets as widgets


class ButtonWrappingTests(unittest.TestCase):
    def button(self,text,width=180):
        button=object.__new__(widgets.CTkButton)
        button.logical_text=text;button._current_width=width;button._get_widget_scaling=lambda:1
        button._minimum_height=48;button._button_wrap_key=None
        button._text_label=SimpleNamespace(cget=lambda key:('Arial',14),configure=Mock(),
            update_idletasks=Mock(),winfo_reqheight=lambda:72)
        return button

    def test_long_logical_label_wraps_and_grows_above_minimum_target(self):
        original='doctor one two three four five six seven eight nine'
        button=self.button(original)
        font=SimpleNamespace(measure=lambda text:len(text)*8,metrics=lambda key:20)
        with patch.object(widgets,'_VISUAL',True),patch.object(widgets.tkfont,'Font',return_value=font), \
             patch.object(widgets.native.CTkButton,'configure') as configure:
            button._refresh_text()
            rendered=configure.call_args.kwargs
            self.assertIn('\n',rendered['text']);self.assertGreater(rendered['height'],48)
            self.assertEqual(original,button.logical_text)
            self.assertEqual(original.split(),rendered['text'].split())

    def test_narrower_width_rewraps_but_unchanged_layout_does_not_loop(self):
        button=self.button('one two three four five six seven eight nine ten',300)
        font=SimpleNamespace(measure=lambda text:len(text)*8,metrics=lambda key:20)
        with patch.object(widgets,'_VISUAL',True),patch.object(widgets.tkfont,'Font',return_value=font), \
             patch.object(widgets.native.CTkButton,'configure') as configure:
            button._refresh_text();height=configure.call_args.kwargs['height']
            button._refresh_text();self.assertEqual(1,configure.call_count)
            button._current_width=100;button._refresh_text()
            self.assertGreater(configure.call_args.kwargs['height'],height)

    def test_native_wrapping_keeps_logical_arabic_and_uses_requested_text_height(self):
        text='طبيب ذو اسم طويل يحتاج إلى الالتفاف'
        button=self.button(text)
        with patch.object(widgets,'_VISUAL',False),patch.object(widgets.tkfont,'Font'), \
             patch.object(widgets.native.CTkButton,'configure') as configure:
            button._refresh_text()
            self.assertEqual(text,configure.call_args.kwargs['text'])
            self.assertEqual(88,configure.call_args.kwargs['height'])
            button._text_label.configure.assert_called_once_with(wraplength=156,justify='center')


if __name__=='__main__':unittest.main()
