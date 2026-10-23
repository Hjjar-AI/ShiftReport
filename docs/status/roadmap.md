# Remaining work

[Current state/limits](current-state.md) · [Completed work](changelog.md) · [Optional UI](../decisions/android-ui-options.md)

## Active — Android doctor CSV import

The original private CSV now passes production ViewModel preview and commit into an
isolated test registry; a synthetic 33-row ContentProvider import also passes.
The reported failure after system-picker selection remains unreproduced and its
exception is unavailable. Capture that failing flow before changing import logic;
preserve identity, clinical-reference and authorization checks.

## P0 — production signing

Owner must supply the protected production key/certificate before distribution.
Local/CI signing inputs exist without debug fallback; no production-signed artifact
is verified. [Signing inputs](../guides/android-build.md#production-release-signing).

## Far future — optional voice dictation

Start with field-by-field diagnosis/treatment/follow-up, then guided sections and
possibly whole-handoff suggestions. Prefer local processing, keep typing available,
insert editable drafts and require visual review/Save. Assess Syrian Arabic, mixed
Arabic/English, medication names/doses, language availability and phone/storage needs.

Options: Android explicit on-device `SpeechRecognizer`; multilingual Whisper through
`whisper.cpp`; or Sherpa-ONNX with an Arabic-capable model. Cloud recognition such as
Google requires deliberate external audio processing, connectivity, credentials and
usage costs. Whole-handoff mapping could use Arabic section labels or a language
model, always with user review.

References: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer),
[whisper.cpp](https://github.com/ggml-org/whisper.cpp), [Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/index.html),
[Google languages](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages).

## Far far future — optional specialty templates

Presentation-only department presets: psychiatry (mental state/sleep/behavior/response/
observation), medicine (problems/investigations/response), surgery (procedure/wound/
drains/diet/mobility), pediatrics (weight/feeding/hydration). Admins could configure
field order/emphasis, prompts/outlines, suggested badges and headings with preview.
Keep diagnosis/treatment/follow-up/labs storage/report mappings fixed; no automatic
findings, prescribing, deletion or hidden clinical content. New searchable measurements/
clinical fields require separate model/persistence/sync/report authorization.

Share stable template IDs/names/presentation through an optionally project-encrypted
Telegram file referenced by pinned state; cache locally, fetch during ordinary sync,
store a project default and use reviewed admin last-successful-write-wins publication.
Missing/unsupported templates fall back to standard; older clients need compatibility
handling. Apply updates to new forms only, preserving open-editor layouts/drafts and
all saved/report clinical content regardless of hidden presentation fields.
