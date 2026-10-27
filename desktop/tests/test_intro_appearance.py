"""Desktop intro lifecycle, credits/resources and softer light surfaces."""
import copy
import unittest
from unittest.mock import Mock, patch
import desktop_appearance as appearance
from desktop_intro import LaunchIntro, load_emblem, SUPERVISOR, CONTRIBUTORS, ORIGINAL_AUTHOR
from tests.ui_support import toolkit, Widget, descendants


class IntroTests(unittest.TestCase):
    def setUp(self):
        self.ui=toolkit();self.addCleanup(self.ui.close)
        self.root=Widget();self.ready=Mock();self.clock=100.0
        self.time_patch=patch('desktop_intro.time.monotonic',side_effect=lambda:self.clock)
        self.time_patch.start();self.addCleanup(self.time_patch.stop)
        self.worker=Mock();self.worker.submit.return_value.done.return_value=False
        self.pool_patch=patch('desktop_intro.ThreadPoolExecutor',return_value=self.worker)
        self.pool_patch.start();self.addCleanup(self.pool_patch.stop)
        self.intro=LaunchIntro(self.root,self.ready)

    def test_intro_keeps_original_credits_and_no_version_label(self):
        text='\n'.join(widget.cget('text') or '' for widget in descendants(self.root))
        for credit in (SUPERVISOR,*CONTRIBUTORS,ORIGINAL_AUTHOR):self.assertIn(credit,text)
        self.assertNotIn('الإصدار',text)

    def test_automatic_dismissal_uses_original_five_second_deadline(self):
        self.clock=104;self.intro.expire();self.ready.assert_not_called()
        self.assertEqual(105,self.intro.deadline)
        self.clock=105;self.intro.expire();self.ready.assert_called_once()
        self.assertFalse(self.intro.frame.winfo_exists())

    def test_expired_hold_waits_only_until_release_without_extending_deadline(self):
        self.intro.press();self.clock=106;self.intro.expire();self.ready.assert_not_called()
        self.intro.release();self.ready.assert_called_once();self.assertEqual(105,self.intro.deadline)

    def test_quick_tap_and_duplicate_timeout_create_workspace_once(self):
        self.intro.press();self.intro.release();self.intro.expire();self.intro.finish()
        self.ready.assert_called_once();self.worker.shutdown.assert_called_once_with(wait=False)

    def test_close_cancels_startup_without_opening_workspace(self):
        self.intro.close();self.ready.assert_not_called();self.assertFalse(self.root.winfo_exists())
        self.assertEqual({},self.root.timers)

    def test_original_emblem_is_available_to_source_and_packaged_resource_loader(self):
        image=load_emblem();self.assertGreater(image.width,100);self.assertGreater(image.height,100)


class LightThemeTests(unittest.TestCase):
    def setUp(self):
        self.before=copy.deepcopy(appearance.native.ThemeManager.theme)
        self.addCleanup(setattr,appearance.native.ThemeManager,'theme',self.before)

    def test_light_fields_have_distinct_surfaces_and_visible_borders_for_all_accents(self):
        def luminance(color):
            channels=[int(color[index:index+2],16)/255 for index in (1,3,5)]
            channels=[value/12.92 if value<=.04045 else ((value+.055)/1.055)**2.4 for value in channels]
            return sum(value*weight for value,weight in zip(channels,(.2126,.7152,.0722)))
        for accent in appearance.PALETTES:
            appearance.set_palette_theme(accent);theme=appearance.native.ThemeManager.theme
            with self.subTest(accent=accent):
                self.assertEqual(appearance.LIGHT_BACKGROUND,theme['CTk']['fg_color'][0])
                self.assertNotEqual('#FFFFFF',theme['CTkFrame']['fg_color'][0])
                self.assertNotEqual(appearance.LIGHT_FIELD,theme['CTkFrame']['fg_color'][0])
                for name in ('CTkEntry','CTkTextbox','CTkComboBox'):
                    self.assertEqual(appearance.LIGHT_FIELD,theme[name]['fg_color'][0])
                    self.assertEqual(appearance.LIGHT_BORDER,theme[name]['border_color'][0])
                    self.assertGreaterEqual(theme[name]['border_width'],2)
                    field=luminance(theme[name]['fg_color'][0]);border=luminance(theme[name]['border_color'][0])
                    self.assertGreaterEqual((max(field,border)+.05)/(min(field,border)+.05),3)

    def test_dark_color_channels_and_clinical_roles_are_preserved(self):
        from desktop_tokens import CLINICAL_URGENT,CLINICAL_WARNING
        original=(CLINICAL_URGENT,CLINICAL_WARNING)
        appearance.native.set_default_color_theme('blue');before=copy.deepcopy(appearance.native.ThemeManager.theme)
        appearance.set_palette_theme()
        after=appearance.native.ThemeManager.theme
        for name in ('CTkEntry','CTkTextbox','CTkComboBox'):
            for key in ('fg_color','border_color','text_color'):self.assertEqual(before[name][key][1],after[name][key][1])
        self.assertEqual(original,(CLINICAL_URGENT,CLINICAL_WARNING))


if __name__=='__main__':unittest.main()
