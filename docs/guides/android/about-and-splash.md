# Edit About and splash content

Edit [strings_about.xml](../../../android/app/src/main/res/values/strings_about.xml), used by both screens. Rebuild/install to see changes; agents need [build approval](../../../AGENTS.md). Wording edits need no version/database/connection changes.

Change text inside the tags; preserve resource names:

| Resource | Appears in |
|---|---|
| `about_app_title` | Main title on both screens |
| `about_subtitle`, `about_splash_subtitle` | About/splash subtitles; empty hides them (splash currently empty) |
| `about_supervisor_role`, `about_supervisor_name` | Shared supervision/development credit |
| `about_technical_role`, `about_splash_technical_role` | About/splash technical-credit headings |
| `about_contributors_prefix` | About prefix above contributor names; may be empty |
| `about_technical_contributors` | Shared names, one `<item>` per person in display order |
| `about_testers_role`, `about_testers` | Heading/names; one `<item>` per tester, Arabic alphabetical order. Comma-separated paragraph with nonbreaking spaces within names |
| `about_original_role`, `about_original_author_name` | Shared original Excel/report credit |
| Remaining `about_*` strings | Page/version labels and accessibility descriptions |

Example:

```xml
<string name="about_app_title">عنوان التطبيق هنا</string>
```

Add/remove `<item>` lines within existing arrays; do not duplicate arrays. Preserve logo/credits unless their change is intended.

Use UTF-8 Arabic, intact XML tags, `&amp;` for `&`, `&lt;` for `<`, `\n` for line breaks, `\'` for literal apostrophes and `\"` for literal double quotes.

Version comes from `android/gradle/app-version.properties`; artwork, typography, spacing, animation and three-second splash timing remain in screen/resource code.

## App wordmark and launcher bitmaps

The launcher label and accessible app title use plain «مناوب»; the bitmap contains «مُنَاوِبْ» with spaced vowel marks, black lettering, white glyph outlines and transparent surroundings. [Branding source and prompts](../../../samples/previews/branding/README.md) retain the selected artwork and earlier candidates.

`AppBrandMark` displays `drawable-nodpi/app_wordmark.png` at its natural aspect ratio in splash, About and the temporary privacy screen. About keeps the new app wordmark and all credits; the old hospital emblem and both old emblem images have been removed from Android build resources. `app_launcher_bitmap.png` supplies the API26+ bitmap wrappers; five density folders contain regular/round lossless WebP icons, with padding for circular masks. Neither artwork is tinted by the selected theme.

To regenerate only these image resources with Pillow already available:

```bash
python3 samples/previews/branding/prepare_android_bitmaps.py
```

`SplashIntroScreen.kt` fades the wordmark in while sliding it horizontally from the RTL start edge (right) over `UiMotion.splashWordmarkMillis` (650ms). Reduced motion shows it immediately. Entrance state survives recreation; `LaunchSplashState` still owns the independent three-second deadline, tap skip and expired-hold release behavior. No white backing card surrounds the wordmark.
