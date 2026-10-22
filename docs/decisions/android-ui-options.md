# Android UI decisions

This is the durable record of the 43 options discussed with the owner. Numbers
match the chat questionnaire. Selected work belongs in the implementation record;
unselected options remain questions, not an authorized backlog. “Pending” means
keep current behavior until the owner chooses otherwise.

## Selected scope — implemented

| Chat option | Selected change | Boundary |
|---|---|---|
| 2 | One explicit dialog state | Retain original revisions, drafts and nested reviews. |
| 5 | Navigation callback group and shared admin route guard | Live mutation authorization stays below navigation. |
| 6 | Explicit patient-card state/actions | No implicit action remapping. |
| 8 | Production ward screen backed by an injected demo repository | Memory only; no Room, Telegram or real settings/session dependencies. |
| 10 | Shared chips | Informational clinical badges and selected filters retain different semantics. |
| 12 | Shared empty states/cards; remove forwarding wrappers | Keep specialized content and protected splash/About. |
| 14 | Shared color containers, button variants and dividers | Clinical severity remains independent of accent. |
| 15 | Spacing tokens/gutters | Compact dimensions and 48dp targets remain. |
| 17 | Genuine font weights and shared emphasis | Keep Tajawal; bundle Medium 500, use genuine Bold 700 for emphasis. Upstream has no 600/SemiBold file. A different family needs a separate choice. |
| 24 | Compact ward controls | Preserve visible delivery warnings, requested fetch reminder, search and all filter choices. |
| 25 | Two-column dashboard tiles | Stack in narrow panes/large text; retain all counts/actions. |
| 31 | Tooltips and directional icons | Use the installed Compose APIs; retain Arabic labels/native RTL. |

Build/compilation checks are now authorized under [AGENTS.md](../../AGENTS.md):
reuse installed/pinned tools and durable caches, try offline, disable automatic
version reservation, retain concise logs, and request one-time installation if a
prerequisite is missing. This does **not** authorize test-suite work, migrations,
version upgrades, packaging/distribution or repeated temporary downloads.

Verification: final offline production Kotlin/resource/KSP compilation passed.
Device rendering, touch/TalkBack/fold behavior and performance remain unverified.

## Remaining options and answers needed

| # | Question still open | Recommendation |
|---|---|---|
| 1 | Further split large screens and inline settings/doctor dialogs? | Only when a cohesive responsibility emerges. |
| 3 | Split WardUiState into content/preferences/operations? | Keep current structure for now. |
| 4 | Replace snackbar state with an event stream? | Keep retained errors/status; no evidenced event loss. |
| 7 | Rename opaque string keys/Arabic label helpers and clean qualified calls broadly? | Gradual semantic naming when touched. |
| 9 | Separate static and expandable header APIs? | Keep until a caller requires it. |
| 11 | Universal drawer/destination/choice row? | Preserve different accessibility roles. |
| 13 | Universal screen/dialog/sheet scaffolds? | Share substantial repetition only. |
| 16 | Increase padding throughout cards/sections/notices? | Keep compact defaults; review rendered examples first. |
| 18 | Change font padding/line-height alignment? | Verify Arabic diacritics/wrapping first. |
| 19 | Darken decorative borders/surface separation? | Visual preference; current field outlines are stronger already. |
| 20 | Apply compact density beyond patient cards? | Keep card-only preference unless an app-wide mode is desired. |
| 21 | Add uniform expansion animation? | Keep restrained motion and immediate bulk expansion. |
| 22 | Make collapsed patient cards much shorter? | Choose the exact visible clinical summary before trimming. |
| 23 | Rewrite adaptive layout and/or narrow the visual divider? | Consider divider-only change; retain fold support and 48dp target. |
| 26 | Make the editor a navigation destination? | Existing full-screen editor already retains drafts across Activity recreation. |
| 27 | Dedicated delivery-resolution sheet/screen? | Keep clarified, confirmed decisions unless more space is wanted. |
| 28 | Remove drawer shortcuts also found in Settings? | Keep requested quick access. |
| 29 | Always show freshness details? | Keep expandable routine detail. |
| 30 | Custom PIN keypad/dots? | Keep native numeric password keyboard. |
| 32 | Shorter TalkBack descriptions/fewer custom actions? | Assess with TalkBack before removing useful actions. |
| 33 | Change the font-scale multiplier/cap? | Review 2× Arabic layouts first. |
| 34 | Debounce search? | Keep immediate cached search. |
| 35 | Further clock/recomposition optimization? | Use performance evidence to choose the next change. |
| 36 | Replace platform theme/consolidate startup themes further? | Do not open encrypted repositories before the storage gate. |
| 37 | Authorize test-suite inspection/editing for filters/gates/editor retention? | **Needs explicit answer**; build permission does not cover it. |
| 38 | Add more previews and a debug-only visual catalog? | Useful, but not selected yet. |
| 39 | Enable cached build/compiler verification? | **Answered: allowed**, with installed tools/offline caches and no version reservation. Performance-report work is optional, not required. |
| 40 | Prepare a toolchain/dependency upgrade proposal? | **Needs answer**; no versions changed. |
| 41 | Keep five-second splash, bypass for returning users, or replace with platform splash? | Keep required hold/skip/deadline behavior unless explicitly changed. |
| 42 | Extract protected splash/About literals/colors without visual/content changes? | **Needs answer**; preserve logo and credits meanwhile. |
| 43 | Rename application/theme/package identifiers? | **Needs answer**; internal names do not affect displayed branding. |

## References

- [Current implementation and verification](../current-state.md)
- [Implementation summary](../changelog.md)
- [Original finding dispositions](../reviews/android-deep-ui-review-validation.md)
- [Maintained UI work guide](../../android/chick.md)
- [Design catalog](../design-catalog.md)

Font provenance: [Google Fonts Tajawal](https://github.com/google/fonts/tree/main/ofl/tajawal).
The font is a permanent bundled asset under its existing SIL Open Font License;
it is unrelated to temporary verification downloads.
