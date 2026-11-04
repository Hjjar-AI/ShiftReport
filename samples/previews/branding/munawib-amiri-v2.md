# مُنَاوِبْ — Amiri-style icon revision

Created with the built-in image generation tool on 4 November 2026.

Output: `munawib-amiri-v2.png`.

Removed Ψ and retained the brain motif, flanking strokes and black-on-white palette. The generation used `munawib-amiri-wordmark-reference.png`, rendered directly from Amiri Bold with Arabic shaping, to guide the lettering. The generated icon approximates that typography; it is not a deterministic font rendering. Font reference source: https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Bold.ttf (downloaded only to `/tmp`, not installed).

Existing launcher assets and the original candidate are preserved. Visually inspected the revised candidate; no Android build, test or device/mask check was run.

## Final generation prompt

Use case: text-localization.
Asset type: final single square Android icon revision.
Image 1: edit target, black مُنَاوِبْ wordmark and brain icon on white. Image 2: exact typography reference, the word مُنَاوِبْ rendered using the real Amiri Bold font. This reference is authoritative for the new lettering.
Change only the wordmark typography in image 1 to closely match the actual Amiri Bold letterforms, proportions, curves, stroke contrast, counters, terminals, dots and vowel marks shown in image 2. Copy the slender upright alif, the smaller meem loop, the narrow graceful waw and its descending curve, and the gently curved bowl of final baa. Preserve the natural letter spacing and correct joins from image 2. Scale the wordmark uniformly to fit above the brain. Do not reuse the chunky lettering of image 1. Do not embolden or stretch the reference glyphs. Do not convert it to Thuluth.
Text exactly "مُنَاوِبْ", with all its dots and vowel marks as in image 2, no extra text.
Keep the distinctive brain silhouette from image 1 and its curved white internal grooves, keep the two flanking curved lower strokes, keep the flat solid black ink on clean opaque pure white background. Keep a balanced centered composition and generous outer margins for an Android launcher icon. The Arabic name is the focus, the brain is beneath it.
No Ψ or any substitute symbol above the word, no additional symbols, no colors, no gradients, no shadows or 3D, no frames, mockups or watermark. Deliver just one finished square icon.

