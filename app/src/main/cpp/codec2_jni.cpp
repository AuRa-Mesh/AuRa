#include <jni.h>
#include <cstring>
#include <mutex>

#include <codec2.h>

namespace {

std::mutex g_mutex;
struct CODEC2 *g_codec2 = nullptr;
int g_samples_per_frame = 0;
int g_bytes_per_frame = 0;

void ensure_codec2_locked() {
    if (g_codec2 != nullptr) return;
    g_codec2 = codec2_create(CODEC2_MODE_3200);
    if (g_codec2 == nullptr) return;
    g_samples_per_frame = codec2_samples_per_frame(g_codec2);
    g_bytes_per_frame = codec2_bytes_per_frame(g_codec2);
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_aura_voice_Codec2Bridge_nativeEncodePcm8kMono(JNIEnv *env, jclass, jshortArray pcm) {
    if (pcm == nullptr) return env->NewByteArray(0);
    jsize n = env->GetArrayLength(pcm);
    std::lock_guard<std::mutex> lock(g_mutex);
    ensure_codec2_locked();
    if (g_codec2 == nullptr || g_samples_per_frame <= 0 || g_bytes_per_frame <= 0) {
        return env->NewByteArray(0);
    }
    if (n < g_samples_per_frame) return env->NewByteArray(0);
    const int frames = static_cast<int>(n / g_samples_per_frame);
    if (frames <= 0) return env->NewByteArray(0);
    const jsize outLen = frames * g_bytes_per_frame;
    jbyteArray out = env->NewByteArray(outLen);
    if (out == nullptr) return nullptr;
    jshort *p = env->GetShortArrayElements(pcm, nullptr);
    jbyte *o = env->GetByteArrayElements(out, nullptr);
    if (p && o) {
        auto *speech = new short[g_samples_per_frame];
        auto *bytes = new unsigned char[g_bytes_per_frame];
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < g_samples_per_frame; i++) {
                speech[i] = p[f * g_samples_per_frame + i];
            }
            codec2_encode(g_codec2, bytes, speech);
            for (int b = 0; b < g_bytes_per_frame; b++) {
                o[f * g_bytes_per_frame + b] = static_cast<jbyte>(bytes[b]);
            }
        }
        delete[] speech;
        delete[] bytes;
    }
    if (p) env->ReleaseShortArrayElements(pcm, p, JNI_ABORT);
    if (o) env->ReleaseByteArrayElements(out, o, 0);
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_aura_voice_Codec2Bridge_nativeSamplesPerFrame(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ensure_codec2_locked();
    if (g_codec2 == nullptr || g_samples_per_frame <= 0) {
        return 160;
    }
    return static_cast<jint>(g_samples_per_frame);
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_example_aura_voice_Codec2Bridge_nativeDecodeToPcm8kMono(JNIEnv *env, jclass, jbyteArray encoded) {
    if (encoded == nullptr) return env->NewShortArray(0);
    jsize len = env->GetArrayLength(encoded);
    std::lock_guard<std::mutex> lock(g_mutex);
    ensure_codec2_locked();
    if (g_codec2 == nullptr || g_samples_per_frame <= 0 || g_bytes_per_frame <= 0) {
        return env->NewShortArray(0);
    }
    if (len < g_bytes_per_frame || (len % g_bytes_per_frame) != 0) return env->NewShortArray(0);
    const int frames = static_cast<int>(len / g_bytes_per_frame);
    const jsize outSamples = frames * g_samples_per_frame;
    jshortArray out = env->NewShortArray(outSamples);
    if (out == nullptr) return nullptr;
    jbyte *e = env->GetByteArrayElements(encoded, nullptr);
    jshort *s = env->GetShortArrayElements(out, nullptr);
    if (e && s) {
        auto *speech = new short[g_samples_per_frame];
        auto *bytes = new unsigned char[g_bytes_per_frame];
        for (int f = 0; f < frames; f++) {
            for (int b = 0; b < g_bytes_per_frame; b++) {
                bytes[b] = static_cast<unsigned char>(e[f * g_bytes_per_frame + b]);
            }
            codec2_decode(g_codec2, speech, bytes);
            for (int i = 0; i < g_samples_per_frame; i++) {
                s[f * g_samples_per_frame + i] = speech[i];
            }
        }
        delete[] speech;
        delete[] bytes;
    }
    if (e) env->ReleaseByteArrayElements(encoded, e, JNI_ABORT);
    if (s) env->ReleaseShortArrayElements(out, s, 0);
    return out;
}
