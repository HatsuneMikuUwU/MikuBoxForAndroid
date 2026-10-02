#include <jni.h>
#include <cstdlib>
#include <cstring>

extern "C" {
void MihomoSetProcessResolver(void* resolver);
void MihomoSetSocketProtector(void* protector);
void MihomoUpdateSystemDNS(char* addresses);
int MihomoStart(char* config, char* home, int tun_fd, char* dns_override, char* overrides_json);
int MihomoAttachTun(int tun_fd);
void MihomoStop();
char* MihomoLastError();
char* MihomoVersion();
char* MihomoTraffic();
char* MihomoProxies();
int MihomoSelectProxy(char* group, char* name);
char* MihomoProxyDelay(char* name, char* url, int timeout_ms);
char* MihomoProxyEndpoint(char* name);
char* MihomoValidateDns(char* dns_yaml);
char* MihomoEvaluateScript(char* config, char* script);
char* MihomoGroupOrder();
char* MihomoRules();
char* MihomoRuntimeInfo();
int MihomoSetMode(char* mode);
char* MihomoTrafficByProxy();
char* MihomoConnections();
int MihomoCloseConnections();
int MihomoCloseConnection(char* id);
}

static JavaVM* java_vm = nullptr;
static jclass resolver_class = nullptr;
static jmethodID resolve_method = nullptr;

static char* resolvePackage(int protocol, const char* src, int sp, const char* dst, int dp, int uid) {
    JNIEnv* env = nullptr;
    bool attached = false;
    if (java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
        attached = true;
    }
    char* result = nullptr;
    if (env->PushLocalFrame(8) == JNI_OK) {
        auto source = env->NewStringUTF(src);
        auto destination = env->NewStringUTF(dst);
        if (source && destination) {
            auto value = static_cast<jstring>(env->CallStaticObjectMethod(resolver_class, resolve_method,
                protocol, source, sp, destination, dp, uid));
            if (!env->ExceptionCheck() && value) {
                const char* chars = env->GetStringUTFChars(value, nullptr);
                if (chars) { result = strdup(chars); env->ReleaseStringUTFChars(value, chars); }
            }
        }
        env->PopLocalFrame(nullptr);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) java_vm->DetachCurrentThread();
    return result;
}

static jclass network_class = nullptr;
static jmethodID protect_method = nullptr;
static int protectSocket(int fd) {
    JNIEnv* env = nullptr;
    bool attached = false;
    if (java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return 0;
        attached = true;
    }
    bool result = env->CallStaticBooleanMethod(network_class, protect_method, fd);
    if (env->ExceptionCheck()) { env->ExceptionClear(); result = false; }
    if (attached) java_vm->DetachCurrentThread();
    return result ? 1 : 0;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    auto local = env->FindClass("com/mikubox/mihomo/core/AndroidProcessResolver");
    if (!local) return JNI_ERR;
    resolver_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    if (!resolver_class) return JNI_ERR;
    resolve_method = env->GetStaticMethodID(resolver_class, "resolve", "(ILjava/lang/String;ILjava/lang/String;II)Ljava/lang/String;");
    if (!resolve_method) return JNI_ERR;
    auto network_local = env->FindClass("com/mikubox/mihomo/core/AndroidNetworkBridge");
    if (!network_local) return JNI_ERR;
    network_class = static_cast<jclass>(env->NewGlobalRef(network_local));
    env->DeleteLocalRef(network_local);
    if (!network_class) return JNI_ERR;
    protect_method = env->GetStaticMethodID(network_class, "protect", "(I)Z");
    if (!protect_method) return JNI_ERR;
    java_vm = vm;
    MihomoSetSocketProtector(reinterpret_cast<void*>(protectSocket));
    MihomoSetProcessResolver(reinterpret_cast<void*>(resolvePackage));
    return JNI_VERSION_1_6;
}

// Scoped owner for GetStringUTFChars. GetStringUTFChars can fail under memory
// pressure and return nullptr with an OutOfMemoryError pending; handing that
// null into the cgo entry points would crash the process, so callers check
// get() and return early — the pending exception then propagates as normal.
class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring str)
            : env_(env), str_(str), chars_(env->GetStringUTFChars(str, nullptr)) {}
    ~UtfChars() {
        if (chars_ != nullptr) env_->ReleaseStringUTFChars(str_, chars_);
    }
    UtfChars(const UtfChars&) = delete;
    UtfChars& operator=(const UtfChars&) = delete;
    const char* get() const { return chars_; }
private:
    JNIEnv* env_;
    jstring str_;
    const char* chars_;
};

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeStart(
        JNIEnv* env, jobject /* thiz */, jstring config, jstring home, jint tun_fd,
        jstring dns_override, jstring overrides_json) {
    UtfChars config_chars(env, config);
    UtfChars home_chars(env, home);
    UtfChars dns_chars(env, dns_override);
    UtfChars overrides_chars(env, overrides_json);
    if (!config_chars.get() || !home_chars.get() || !dns_chars.get() || !overrides_chars.get()) {
        return -1;
    }
    return MihomoStart(
            const_cast<char*>(config_chars.get()), const_cast<char*>(home_chars.get()), tun_fd,
            const_cast<char*>(dns_chars.get()), const_cast<char*>(overrides_chars.get()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeStop(JNIEnv* /* env */, jobject /* thiz */) {
    MihomoStop();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeAttachTun(JNIEnv* /* env */, jobject /* thiz */, jint fd) {
    return MihomoAttachTun(fd);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeLastError(JNIEnv* env, jobject /* thiz */) {
    char* error = MihomoLastError();
    jstring result = env->NewStringUTF(error == nullptr ? "" : error);
    std::free(error);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeVersion(JNIEnv* env, jobject /* thiz */) {
    char* version = MihomoVersion();
    jstring result = env->NewStringUTF(version == nullptr ? "unknown" : version);
    std::free(version);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeTraffic(JNIEnv* env, jobject /* thiz */) {
    char* traffic = MihomoTraffic();
    jstring result = env->NewStringUTF(traffic == nullptr ? "{}" : traffic);
    std::free(traffic);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeProxies(JNIEnv* env, jobject /* thiz */) {
    char* proxies = MihomoProxies();
    jstring result = env->NewStringUTF(proxies == nullptr ? "{}" : proxies);
    std::free(proxies);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeSelectProxy(
        JNIEnv* env, jobject /* thiz */, jstring group, jstring name) {
    UtfChars group_chars(env, group);
    UtfChars name_chars(env, name);
    if (!group_chars.get() || !name_chars.get()) {
        return -1;
    }
    return MihomoSelectProxy(
            const_cast<char*>(group_chars.get()), const_cast<char*>(name_chars.get()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeProxyDelay(
        JNIEnv* env, jobject /* thiz */, jstring name, jstring url, jint timeout_ms) {
    UtfChars name_chars(env, name);
    UtfChars url_chars(env, url);
    if (!name_chars.get() || !url_chars.get()) {
        return env->NewStringUTF("{\"error\":\"oom\"}");
    }
    char* delay = MihomoProxyDelay(
            const_cast<char*>(name_chars.get()), const_cast<char*>(url_chars.get()), timeout_ms);
    jstring result = env->NewStringUTF(delay == nullptr ? "{\"error\":\"null\"}" : delay);
    std::free(delay);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeValidateDns(
        JNIEnv* env, jobject /* thiz */, jstring dns_yaml) {
    UtfChars dns_chars(env, dns_yaml);
    if (!dns_chars.get()) {
        return env->NewStringUTF("out of memory");
    }
    char* err = MihomoValidateDns(const_cast<char*>(dns_chars.get()));
    jstring result = env->NewStringUTF(err == nullptr ? "" : err);
    std::free(err);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeGroupOrder(JNIEnv* env, jobject /* thiz */) {
    char* order = MihomoGroupOrder();
    jstring result = env->NewStringUTF(order == nullptr ? "[]" : order);
    std::free(order);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeRules(JNIEnv* env, jobject /* thiz */) {
    char* rules = MihomoRules();
    jstring result = env->NewStringUTF(rules == nullptr ? "[]" : rules);
    std::free(rules);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeRuntimeInfo(JNIEnv* env, jobject /* thiz */) {
    char* info = MihomoRuntimeInfo();
    jstring result = env->NewStringUTF(info == nullptr ? "{}" : info);
    std::free(info);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeSetMode(
        JNIEnv* env, jobject /* thiz */, jstring mode) {
    UtfChars mode_chars(env, mode);
    if (!mode_chars.get()) {
        return -1;
    }
    return MihomoSetMode(const_cast<char*>(mode_chars.get()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeTrafficByProxy(JNIEnv* env, jobject /* thiz */) {
    char* traffic = MihomoTrafficByProxy();
    jstring result = env->NewStringUTF(traffic == nullptr ? "{}" : traffic);
    std::free(traffic);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeConnections(JNIEnv* env, jobject /* thiz */) {
    char* connections = MihomoConnections();
    jstring result = env->NewStringUTF(connections == nullptr ? "{}" : connections);
    std::free(connections);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeCloseConnections(JNIEnv* /* env */, jobject /* thiz */) {
    return MihomoCloseConnections();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeCloseConnection(
        JNIEnv* env, jobject /* thiz */, jstring id) {
    UtfChars id_chars(env, id);
    if (!id_chars.get()) {
        return -1;
    }
    return MihomoCloseConnection(const_cast<char*>(id_chars.get()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeUpdateSystemDns(JNIEnv* env, jobject, jstring addresses) {
    UtfChars value(env, addresses);
    if (value.get()) MihomoUpdateSystemDNS(const_cast<char*>(value.get()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeEvaluateScript(JNIEnv* env, jobject, jstring config, jstring script) {
    const char* c = env->GetStringUTFChars(config, nullptr);
    const char* s = env->GetStringUTFChars(script, nullptr);
    if (!c || !s) {
        if (c) env->ReleaseStringUTFChars(config, c);
        if (s) env->ReleaseStringUTFChars(script, s);
        return nullptr;
    }
    char* result = MihomoEvaluateScript(const_cast<char*>(c), const_cast<char*>(s));
    env->ReleaseStringUTFChars(config, c);
    env->ReleaseStringUTFChars(script, s);
    jstring out = env->NewStringUTF(result ? result : "{}");
    free(result);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikubox_mihomo_core_MihomoCore_nativeProxyEndpoint(
        JNIEnv* env, jobject, jstring name) {
    UtfChars chars(env, name);
    if (!chars.get()) return env->NewStringUTF("{}");
    char* value = MihomoProxyEndpoint(const_cast<char*>(chars.get()));
    jstring result = env->NewStringUTF(value ? value : "{}");
    std::free(value);
    return result;
}
