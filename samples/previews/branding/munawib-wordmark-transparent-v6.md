# مُنَاوِبْ — final vowel-mark spacing

Generated with the built-in image generation tool on 4 November 2026.

Output: `munawib-wordmark-transparent-v6.png`.
Reference: `munawib-wordmark-transparent-v5.png`.

Owner requested further separation of the final sukūn and the other vowel marks from the main lettering. Letter dots remain in place. This is the selected source for Android bitmaps; the launcher label is plain «مناوب». `prepare_android_bitmaps.py` derives transparent resources without modifying this original generated candidate.

## Final generation prompt

Use case: precise-object-edit.
Input image 1 is a transparent Arabic wordmark to edit. Preserve the EXACT custom black calligraphy, letterforms, scale, narrow white outlines and the word مُنَاوِبْ. Make ONLY a spacing correction to the FOUR TASHKIL vowel marks, moving them away from the main letters so they read clearly as separate vowel marks at icon size.
- Hollow circular SUKUN ْ above the final baa on the far LEFT: move it visibly farther UP and slightly LEFT than its current position, around 45 pixels up and 20 pixels left on this 1254px canvas. It must float with a generous transparent gap above the leftmost baa swash rather than touch its tip.
- DAMMA ُ above the initial meem on the far RIGHT: move it about 40 pixels UP, maintaining its relationship to meem.
- FATHA َ (the upper diagonal stroke above the noon dot): move it about 35 pixels UP, so it is clearly separated from both the letter dot and the alif.
- KASRA ِ (the lowest diagonal stroke beneath waw near the lower center-left): move it about 35 pixels DOWN, so a clean transparent gap separates it from the long descending waw.
Preserve shapes and sizes of all these marks. Keep the TWO LETTER DOTS (the black diamond above noon and the black diamond below baa) in their original positions; these are not vowel marks. Do not move or change any main letter or letter dot.
Keep only black writing with a precise white outline and truly transparent background including all gaps; no colored residue, haze, shadow, rectangular backing or new ornaments. No brain or Psi. Keep all marks unclipped with generous outer padding. Output one transparent RGBA square PNG.

