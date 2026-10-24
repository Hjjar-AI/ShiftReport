# Remaining verification

[Results](current-state.md#verification-and-limits) · [Android commands](../guides/android-testing.md) · [Device procedures](../guides/device-coverage.md) · [Desktop/live commands](../guides/deep-testing.md)

Single outstanding-check list (Markdown audit: 24 October 2026); not an implementation roadmap or run authorization. Original/source-bearing private artifacts remain preserved. Results belong in Current state; this run’s physical fingerprint success was skipped by owner.

| Work | Remaining check / prerequisite |
|---|---|
| Original doctor CSV failure | Owner's original file, actual OEM provider/picker and displayed exception. Preview/isolated commit and synthetic native selection passed; the original phone failure remains unreproduced. |
| Android accessibility/windows | Full TalkBack reading/order/states; real IME at large system fonts; physical split-screen/fold/unfold with drafts/reviews; demo navigation/local labelled PDF export; other supported OS/vendors, especially API 23–25 bars. Simulated geometry/typography and one API 29 phone are already covered. |
| Android privacy/auth | Source-only PDF preview-return fix: compile and verify external viewer → privacy/PIN return and rotation retain Send readiness, general/each selected supervisor; regeneration/options changes and failed viewer launch still require preview. Physical screenshot/recents protection; background clipboard restrictions/replacement; biometric failure/lock/resume paths beyond the passed native cancel and owner-assisted success cases. |
| Two physical phones / real Telegram | Disposable project: protected join, retrieve/edit/publish order, pending-edit/revision guards, last-successful-write behavior, interrupted upload/index/delivery and uncertain-receipt recovery. Existing two-client tests/harness do not establish two-phone UI behavior. |
| Desktop runtime | Complete Linux clinical editing/dialog/lock workflows and real PDF opener; fresh supported/pinned installation. Current 60-case suite, demo window/Settings/reset and sample PDF generation passed in the existing project venv. |
| Windows/distribution | Windows 7/DPAPI/Tk/fonts/viewer, intended x86/x64 target and packaged startup/resources/CA/tzdata. Second-account shared Android debug signing; protected production key/certificate and actual release-signature verification. [Packaging](../guides/windows-exe-packaging.md), [signing](../guides/android-build.md#production-release-signing). |
| Cross-client end to end | Android ↔ desktop live join/reconnect/publish/report/backup workflows. Current-format encrypted file interop passed again both directions using fresh Android artifacts; live GUI exchanges remain unverified. |
| PDF viewers/printing | Selection/search/copy, internal links, print output and names-only A4/advanced options and remaining font/layout combinations in real viewers. Android classic/A5/task-heavy and fresh desktop Classic/dark-card samples passed generation/selected visual review. |
| Performance | Named-device release baseline/comparison for 200 patients. Existing native-clock debug samples do not establish release performance or a speedup. |
| Live credentials | Desktop live creation/protected join export/login/admin/delivery; BotFather revocation only with owner action and a disposable bot. Saving a replacement token does not revoke the old one. |

No required unimplemented test-creation item was found. O23's screenshot matrix is an [unselected proposal](../decisions/android-ui-options.md), not approved work. Exhaustive fuzzing/mutation coverage, arbitrary clock changes, every crash/power-loss/ disk-full boundary and every PDF glyph are limits, not a new backlog or passing claim.

Use isolated synthetic data; [AGENTS.md](../../AGENTS.md) governs build/test authorization and per-build resource/cache checks. Live work needs an owner-designated disposable project; do not change device settings automatically.
