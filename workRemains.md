# ShiftReport remaining work

This file contains only unfinished implementation work and explicitly deferred proposals. Verification evidence and limitations are recorded in [currentState.md](currentState.md). Completed items are tracked in [workDone.md](workDone.md). Design and architecture basis are documented in [designAndArchitecture.md](designAndArchitecture.md).

## P0 — production signing

1. Configure production signing before distribution.
   - Release currently uses the debug signing configuration. Configure a protected release keystore through local/CI secrets without committing keys or passwords.

## Far future — optional voice dictation

1. Start with field-by-field dictation for diagnosis, treatment, and follow-up.
   - Suggested first approach: Android's explicit on-device `SpeechRecognizer`, checking device/language availability and keeping ordinary typing available.
   - For an app-managed offline engine, consider multilingual Whisper through `whisper.cpp`, or Sherpa-ONNX with a suitable Arabic-capable model. Account for model storage, native integration, and phone processing requirements.
   - Cloud recognition, such as Google Cloud Speech-to-Text, is an alternative only if external audio processing is deliberately chosen; it requires connectivity, service credentials, and usage costs.
   - Prefer local audio processing. Insert speech results as editable drafts and require visual review and explicit Save.

2. Consider guided section-by-section dictation, followed later by whole-handoff field suggestions.
   - Whole-handoff assignment could use Arabic section labels or a separate language model to propose field mappings; retain user review before applying them.
   - Model choice should account for Syrian Arabic, mixed Arabic/English speech, medication names, and doses.

Technology references: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/index.html), and [Google Cloud Speech-to-Text languages](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages).

## Far far future — optional specialty templates

These are design suggestions, not implemented features or near-term requirements.

1. Adapt form presentation to a department while retaining the existing patient fields.
   - Suggested starting point: a configurable psychiatry template with prompts for mental state, sleep, behavior, medication response, and observation needs.
   - Other possible templates: internal medicine (active problems, investigations, treatment response), surgery (procedure/date, wound/drains, diet, mobility), and pediatrics (weight, feeding, hydration).
   - Let administrators configure field order, section emphasis, prompts, text outlines, suggested badges, and report headings, with a preview before publication.
   - Keep diagnosis, treatment, follow-up, and laboratory storage/report mappings fixed. Templates must not automatically assign findings, prescribe treatment, or remove saved clinical content.

2. Share template configuration through the existing Telegram project.
   - Publish a template file containing stable template IDs, names, and presentation settings; add a reference to it in pinned synchronization state.
   - Cache templates locally for offline use and retrieve updates during ordinary synchronization. Admin publication follows the accepted last-successful-write-wins behavior.
   - Encrypt the template file with the existing shared project key when project encryption is enabled.
   - Store a project-default template selection. Compatibility handling is needed for clients that do not understand template settings.

3. Keep patient entry and reports stable across template updates.
   - New form sessions use the current template; open editors retain their layout until closed.
   - Template changes must not rewrite existing patient data. Missing or unsupported templates fall back to the standard form.
   - Reports must retain saved clinical content even when a template hides a field in the editor; template headings cannot override report inclusion rules.
   - Searchable custom measurements or new clinical fields would require separate model, persistence, synchronization, and reporting work beyond presentation templates.
