# Edit About and splash content

Edit [strings_about.xml](../../android/app/src/main/res/values/strings_about.xml). This is the actual content used by both Android screens; this Markdown guide is not loaded by the app. Rebuild and install the app to see changes. No version, database or connection changes are needed for a wording edit.

Change the text between the XML tags, leaving each `name` unchanged:

| Resource | Appears in |
|---|---|
| `about_app_title` | Main title on both screens |
| `about_subtitle` | About subtitle; leave empty to hide |
| `about_splash_subtitle` | Splash subtitle; currently empty/hidden |
| `about_supervisor_role`, `about_supervisor_name` | Supervision/development credit on both screens |
| `about_technical_role` | About technical-credit heading |
| `about_splash_technical_role` | Splash technical-credit heading |
| `about_contributors_prefix` | About prefix above contributor names; may be empty |
| `about_technical_contributors` | Shared name list; one `<item>` per person, in display order |
| `about_testers_role`, `about_testers` | Testers heading and shared name list; one `<item>` per tester. Displayed as one comma-separated paragraph with nonbreaking spaces inside each name |
| `about_original_role`, `about_original_author_name` | Original Excel/report credit on both screens |
| Remaining `about_*` strings | Page/version labels and accessibility descriptions |

For example, edit the existing title line:

```xml
<string name="about_app_title">عنوان التطبيق هنا</string>
```

Keep the testers array in Arabic alphabetical order; both screens use its order.

Add or remove contributor `<item>` lines inside the existing `<string-array>`; do not duplicate the array. Preserve the logo and credits unless intentionally changing them.

Use `&amp;` for `&`, `&lt;` for `<`, and `\n` for an intentional line break. Android string resources require escaping a literal apostrophe as `\'` and a literal double quote as `\"`. Arabic text can be entered directly in UTF-8. Keep the XML tags intact.

The version number remains generated from `android/gradle/app-version.properties`. Logo artwork, typography, spacing, animations and the three-second splash timer stay in the screen/resource implementation; editing these strings changes wording only.
