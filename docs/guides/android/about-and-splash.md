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
