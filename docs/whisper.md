# Optional Whisper speech recognition

Settings, Speaking: **Whisper speech recognition** (off by default).

- **Off**: speaking steps use Android's recogniser, exactly as before.
- **On**: choose a model (Base about 60 MB, Small about 190 MB), then **Download**. Until the model is on the phone the
  switch does nothing and Android's recogniser stays in use. Once it is installed, every speaking step records at 16 kHz,
  stops when you tap the mic or after 1.3 s of quiet, then transcribes on the phone. Audio is never sent anywhere; only the
  model download uses the network (Hugging Face, `ggerganov/whisper.cpp`).
- Only the microphone button changes. Nothing else in lessons, conversations or the oral exam depends on which recogniser ran.

## How it is built

- `app/src/main/cpp`: CMake fetches whisper.cpp v1.7.5 at configure time and builds a small JNI library (`WhisperNative`).
  Only `arm64-v8a` is built; on any other CPU the library does not load and the setting says Whisper is unavailable.
- `speech/WhisperEngine.kt`: model download and validation (ggml magic, minimum size), lazy model load, transcription,
  `WhisperRecorder`. The model is freed when the app goes to the background.
- `domain/speech/WhisperSupport.kt`: `Endpointer` (when to stop listening) and `WhisperText.clean` (drops `[BLANK_AUDIO]`
  and similar tags). Unit tested.

## Known limits

- No live partial text while speaking (Android's recogniser shows it); the screen says "transcribing" afterwards.
- Whisper can silently correct a learner's mistake, as Android's recogniser does; it is not a pronunciation scorer.
- Speed: Base is near real-time on a recent phone; Small is slower. Not yet measured on a device.
