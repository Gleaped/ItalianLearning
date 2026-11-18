// Minimal JNI bridge to whisper.cpp: load a model, transcribe 16 kHz mono float PCM, free.
#include <jni.h>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_gleaped_italianlearning_speech_WhisperNative_init(JNIEnv *env, jclass, jstring path) {
    const char *c_path = env->GetStringUTFChars(path, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(c_path, cparams);
    env->ReleaseStringUTFChars(path, c_path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_gleaped_italianlearning_speech_WhisperNative_free(JNIEnv *, jclass, jlong handle) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_gleaped_italianlearning_speech_WhisperNative_transcribe(
        JNIEnv *env, jclass, jlong handle, jfloatArray samples, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");

    const jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang;
    params.detect_language = false;
    params.no_timestamps = true;
    params.single_segment = false;
    params.n_threads = std::max(1, (int) threads);

    std::string text;
    if (whisper_full(ctx, params, data, n) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) {
            text += whisper_full_get_segment_text(ctx, i);
        }
    }

    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    return env->NewStringUTF(text.c_str());
}

}
