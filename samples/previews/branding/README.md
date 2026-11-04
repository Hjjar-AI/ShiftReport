# Android branding

Selected source: [`munawib-wordmark-transparent-v6.png`](munawib-wordmark-transparent-v6.png), with the [final spacing prompt](munawib-wordmark-transparent-v6.md). The black custom Arabic wordmark retains tashkīl and a narrow white outline; the launcher label is plain «مناوب». The final sukūn is raised and shifted left, with other vowel marks separated from the lettering. Earlier numbered candidates and their prompts retain the owner's design iterations; they are not active app resources.

[`prepare_android_bitmaps.py`](prepare_android_bitmaps.py) derives transparent `app_wordmark.png`, a padded square `app_launcher_bitmap.png`, and five sizes of regular/round lossless WebP launcher icons. It removes near-transparent generation residue, normalizes opaque pixels, trims empty canvas and resizes without changing the selected letterforms. Originals remain intact. Run with an existing Pillow installation:

```bash
python3 samples/previews/branding/prepare_android_bitmaps.py
```

[`AppBrandMark`](../../../android/app/src/main/java/com/hos/rushdpatients/ui/components/AppBrandMark.kt) owns untinted, aspect-preserving display in Android. The [About/splash guide](../../../docs/guides/android/about-and-splash.md#app-wordmark-and-launcher-bitmaps) maps the runtime resources and entrance behavior. Android integration does not replace the institutional emblem retained in About or alter the other clients' branding.

PNG transparency, resource XML and circular-mask geometry were inspected locally. Android compilation/device appearance and animation acceptance are tracked only in the [handoff](../../../docs/status/testing-handoff.md).
