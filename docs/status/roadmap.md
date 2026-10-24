# Remaining work

[Current state/limits](current-state.md) · [Completed work](changelog.md) · [Optional UI](../decisions/android-ui-options.md)

## Active — Android doctor CSV import

Original private CSV preview/isolated commit and synthetic33-row/native provider import pass. Original phone-picker failure remains unreproduced; exception unavailable. Capture actual file/provider/error before changing import logic; preserve identity/reference/authority guards.

## P0 — production signing

Owner protected production key/certificate required. Local/CI inputs exist without debug fallback; signed release unverified. [Inputs](../guides/android-build.md#production-release-signing).

## Far future — optional voice dictation

Field-by-field diagnosis/treatment/follow-up → guided sections → optional whole-handoff suggestions. Prefer local; retain typing/editable drafts/visual review/Save. Evaluate Syrian/mixed Arabic-English,drug names/doses,available languages,device/storage needs.

Candidates: explicit on-device [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), multilingual Whisper/[whisper.cpp](https://github.com/ggml-org/whisper.cpp), Arabic-capable [Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/index.html). [Google cloud recognition](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages) requires deliberate external audio processing,connectivity,credentials/cost. Whole-handoff mapping may use Arabic section labels/language model; always reviewed.

## Far far future — optional specialty templates

Presentation presets: psychiatry(mental state/sleep/behavior/response/observation), medicine(problems/investigations/response), surgery(procedure/wound/drains/diet/mobility), pediatrics(weight/feeding/hydration). Admin preview of field order/emphasis/prompts/outlines/badges/headings. Fixed diagnosis/treatment/follow-up/labs storage/report mappings; no automatic findings,prescribing,deletion or hidden saved content. New searchable measurements/clinical fields need separate model/persistence/sync/report authorization.

Stable template IDs/names/presentation in optionally encrypted Telegram file referenced by pin; local cache/ordinary fetch/project default/reviewed admin last-successful-write-wins. Unsupported/missing → standard; older clients need compatibility handling. Updates apply only to new forms, preserving open drafts/layouts and all saved/report content.
