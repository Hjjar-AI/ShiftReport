# مُنَاوِبْ — adjusted sukūn

Generated with the built-in image generation tool on 4 November 2026.

Output: `munawib-wordmark-transparent-v5.png`.
Reference: `munawib-wordmark-transparent-v4.png`.

Owner requested moving the final baa's sukūn up and left before Android integration. The wordmark retains its tashkīl; the Android launcher label uses plain «مناوب». Production resource derivation is owned by `prepare_android_bitmaps.py` in this directory.

## Final generation prompt

Use case: precise-object-edit.
Asset: transparent Arabic app wordmark.
Input image 1 is the edit target. Make ONLY this small typographic correction:
The small hollow circular sukun mark ْ above the LEFTMOST final baa ب (currently around x=255,y=525 on this square reference) must move a little UP and LEFT: translate this entire existing ring-shaped mark approximately 30 pixels to the left and 45 pixels upward on this 1254px canvas. Keep its shape, size, black fill and narrow white outline identical. This increases the separation from the bowl of final ب so it is clearly a sukun and is not mistaken for a noon dot.
Keep all other letters, all dots, all other vowel marks, every curve, the custom black calligraphy, the narrow white contour, the exact scale and positions of the rest of the word absolutely unchanged. Text remains exactly مُنَاوِبْ. Keep the final baa dot below it unchanged.
Keep the surrounding canvas genuinely fully transparent with no white/gray noisy residue. Only solid black writing and its precise narrow white outline remain. No new symbol, no brain, no Psi, no curves, no shadow or glow. Output one transparent RGBA square PNG.

