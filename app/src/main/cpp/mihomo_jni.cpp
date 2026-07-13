#include <jni.h>
#include <cstdlib>

extern "C" {
int MihomoStart(char* config, char* home, int tun_fd);
void MihomoStop();
char* MihomoLastError();
char* MihomoTraffic();
}

extern "C" JNIEXPORT jint JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeStart(
        JNIEnv* env, jobject /* thiz */, jstring config, jstring home, jint tun_fd) {
    const char* config_chars = env->GetStringUTFChars(config, nullptr);
    const char* home_chars = env->GetStringUTFChars(home, nullptr);
    const int result = MihomoStart(
            const_cast<char*>(config_chars), const_cast<char*>(home_chars), tun_fd);
    env->ReleaseStringUTFChars(config, config_chars);
    env->ReleaseStringUTFChars(home, home_chars);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeStop(JNIEnv* /* env */, jobject /* thiz */) {
    MihomoStop();
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeLastError(JNIEnv* env, jobject /* thiz */) {
    char* error = MihomoLastError();
    jstring result = env->NewStringUTF(error == nullptr ? "" : error);
    std::free(error);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeTraffic(JNIEnv* env, jobject /* thiz */) {
    char* traffic = MihomoTraffic();
    jstring result = env->NewStringUTF(traffic == nullptr ? "{}" : traffic);
    std::free(traffic);
    return result;
}
