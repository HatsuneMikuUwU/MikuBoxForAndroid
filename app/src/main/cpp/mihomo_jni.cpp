#include <jni.h>
#include <cstdlib>

extern "C" {
int MihomoStart(char* config, char* home, int tun_fd, char* dns_override, char* overrides_json);
void MihomoStop();
char* MihomoLastError();
char* MihomoVersion();
char* MihomoTraffic();
char* MihomoProxies();
int MihomoSelectProxy(char* group, char* name);
char* MihomoProxyDelay(char* name, char* url, int timeout_ms);
char* MihomoValidateDns(char* dns_yaml);
}

extern "C" JNIEXPORT jint JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeStart(
        JNIEnv* env, jobject /* thiz */, jstring config, jstring home, jint tun_fd,
        jstring dns_override, jstring overrides_json) {
    const char* config_chars = env->GetStringUTFChars(config, nullptr);
    const char* home_chars = env->GetStringUTFChars(home, nullptr);
    const char* dns_chars = env->GetStringUTFChars(dns_override, nullptr);
    const char* overrides_chars = env->GetStringUTFChars(overrides_json, nullptr);
    const int result = MihomoStart(
            const_cast<char*>(config_chars), const_cast<char*>(home_chars), tun_fd,
            const_cast<char*>(dns_chars), const_cast<char*>(overrides_chars));
    env->ReleaseStringUTFChars(config, config_chars);
    env->ReleaseStringUTFChars(home, home_chars);
    env->ReleaseStringUTFChars(dns_override, dns_chars);
    env->ReleaseStringUTFChars(overrides_json, overrides_chars);
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
Java_top_uwu_mikubox_core_MihomoCore_nativeVersion(JNIEnv* env, jobject /* thiz */) {
    char* version = MihomoVersion();
    jstring result = env->NewStringUTF(version == nullptr ? "unknown" : version);
    std::free(version);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeTraffic(JNIEnv* env, jobject /* thiz */) {
    char* traffic = MihomoTraffic();
    jstring result = env->NewStringUTF(traffic == nullptr ? "{}" : traffic);
    std::free(traffic);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeProxies(JNIEnv* env, jobject /* thiz */) {
    char* proxies = MihomoProxies();
    jstring result = env->NewStringUTF(proxies == nullptr ? "{}" : proxies);
    std::free(proxies);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeSelectProxy(
        JNIEnv* env, jobject /* thiz */, jstring group, jstring name) {
    const char* group_chars = env->GetStringUTFChars(group, nullptr);
    const char* name_chars = env->GetStringUTFChars(name, nullptr);
    const int result = MihomoSelectProxy(
            const_cast<char*>(group_chars), const_cast<char*>(name_chars));
    env->ReleaseStringUTFChars(group, group_chars);
    env->ReleaseStringUTFChars(name, name_chars);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeProxyDelay(
        JNIEnv* env, jobject /* thiz */, jstring name, jstring url, jint timeout_ms) {
    const char* name_chars = env->GetStringUTFChars(name, nullptr);
    const char* url_chars = env->GetStringUTFChars(url, nullptr);
    char* delay = MihomoProxyDelay(
            const_cast<char*>(name_chars), const_cast<char*>(url_chars), timeout_ms);
    env->ReleaseStringUTFChars(name, name_chars);
    env->ReleaseStringUTFChars(url, url_chars);
    jstring result = env->NewStringUTF(delay == nullptr ? "{\"error\":\"null\"}" : delay);
    std::free(delay);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_uwu_mikubox_core_MihomoCore_nativeValidateDns(
        JNIEnv* env, jobject /* thiz */, jstring dns_yaml) {
    const char* dns_chars = env->GetStringUTFChars(dns_yaml, nullptr);
    char* err = MihomoValidateDns(const_cast<char*>(dns_chars));
    env->ReleaseStringUTFChars(dns_yaml, dns_chars);
    jstring result = env->NewStringUTF(err == nullptr ? "" : err);
    std::free(err);
    return result;
}
