# Speaker Verification Android Device Test

## Build

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
```

## Logcat filter

Use the `SpeakerVerification` tag and check these events:

- `SV_REGISTER_SAMPLE`
- `SV_REGISTER_DONE`
- `SV_REGISTER_RESET`
- `SV_STT_GATE_REQUEST reason=... registered=...`
- `SV_VERIFY similarity=... threshold=... accepted=... totalMs=...`
- `SV_STT_GATE accepted=true action=startSpeechRecognizer reason=...`
- `SV_STT_GATE accepted=false action=blocked reason=...`
- `SV_STT_GATE registered=false action=fallback_stt reason=...`

## Test scenarios

1. Open the My Info tab.
2. Tap speaker enrollment and record all 3 enrollment samples.
3. Restart the app and confirm My Info still shows speaker registration as complete.
4. In My Info, run speaker verification test and confirm similarity and pass/reject result update.
5. Start product search from the home screen.
6. Registered speaker speaks the product name or confirmation command.
   - Expected: `SV_STT_GATE_REQUEST`
   - Expected: `SV_VERIFY accepted=true`
   - Expected: `SV_STT_GATE accepted=true action=startSpeechRecognizer`
   - Expected: STT result is processed.
7. Different speaker speaks during the same product search flow.
   - Expected: `SV_VERIFY accepted=false`
   - Expected: `SV_STT_GATE accepted=false action=blocked`
   - Expected: no `SpeechRecognizer.startListening` after rejection.
   - Expected: user sees the registered-user voice warning and can try again.
8. Registered speaker speaks again.
   - Expected: speaker verification runs again for this utterance.
   - Expected: accepted utterance starts STT normally.
9. Reset speaker enrollment from My Info.
10. Start product search again.
    - Expected: `SV_STT_GATE registered=false action=fallback_stt`
    - Expected: STT starts with the existing fallback policy.

## Microphone collision check

- Confirm each accepted STT start happens after `SV_VERIFY` finishes.
- Confirm there is no overlapping AudioRecord verification and SpeechRecognizer recording.
- Current implementation waits 150 ms after speaker verification before starting SpeechRecognizer.
