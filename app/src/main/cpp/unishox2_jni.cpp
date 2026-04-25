#include <jni.h>
#include <cstdint>

#include "unishox2.h"

namespace {

/** Соответствует запасу прошивки / mesh.proto для расшифрованного текста в одном Data; с запасом для UTF-8. */
constexpr int kMeshTextDecompressMax = 512;

static const uint8_t kUsxHcodesDflt[] = {0x00, 0x40, 0x80, 0xC0, 0xE0};
static const uint8_t kUsxHcodeLensDflt[] = {2, 2, 2, 3, 3};

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_aura_meshwire_Unishox2Native_decompressMeshTextNative(JNIEnv *env, jobject /* thiz */, jbyteArray jInput)
{
    if (jInput == nullptr) {
        return nullptr;
    }
    const jsize inLen = env->GetArrayLength(jInput);
    if (inLen <= 0) {
        return nullptr;
    }
    jbyte *inBytes = env->GetByteArrayElements(jInput, nullptr);
    if (inBytes == nullptr) {
        return nullptr;
    }

    char out[kMeshTextDecompressMax + 1];
    const int olenCap = kMeshTextDecompressMax;
    const int n = unishox2_decompress(
        reinterpret_cast<const char *>(inBytes),
        inLen,
        out,
        olenCap,
        kUsxHcodesDflt,
        kUsxHcodeLensDflt,
        USX_FREQ_SEQ_DFLT,
        USX_TEMPLATES);

    env->ReleaseByteArrayElements(jInput, inBytes, JNI_ABORT);

    if (n < 0 || n > olenCap) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(n);
    if (result == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, n, reinterpret_cast<const jbyte *>(out));
    return result;
}

/** Сжатие текста для portnum 7 (как `unishox2_compress_simple` в Router mesh). */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_aura_meshwire_Unishox2Native_compressMeshTextNative(JNIEnv *env, jobject /* thiz */, jbyteArray jInput)
{
    if (jInput == nullptr) {
        return nullptr;
    }
    const jsize inLen = env->GetArrayLength(jInput);
    if (inLen <= 0) {
        return nullptr;
    }
    jbyte *inBytes = env->GetByteArrayElements(jInput, nullptr);
    if (inBytes == nullptr) {
        return nullptr;
    }

    char out[kMeshTextDecompressMax + 1];
    const int n = unishox2_compress_simple(reinterpret_cast<const char *>(inBytes), inLen, out);

    env->ReleaseByteArrayElements(jInput, inBytes, JNI_ABORT);

    if (n <= 0 || n > kMeshTextDecompressMax) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(n);
    if (result == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, n, reinterpret_cast<const jbyte *>(out));
    return result;
}
