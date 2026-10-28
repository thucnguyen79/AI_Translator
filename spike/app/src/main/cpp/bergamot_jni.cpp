// JNI bridge for io.github.thucnguyen79.aitranslator.spike.mt.BergamotNative.
// Holds one BlockingService and one TranslationModel per direction key ("vien", "enzh", ...).

#include <jni.h>
#include <android/log.h>

#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"

using namespace marian::bergamot;

namespace {

std::mutex g_mutex;
std::unique_ptr<BlockingService> g_service;
std::map<std::string, std::shared_ptr<TranslationModel>> g_models;

std::string toString(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

void throwJava(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(cls, message.c_str());
}

BlockingService &service() {
    if (!g_service) {
        BlockingService::Config config;
        config.cacheSize = 0;
        config.logger.level = "off";
        g_service = std::make_unique<BlockingService>(config);
    }
    return *g_service;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_github_thucnguyen79_aitranslator_spike_mt_BergamotNative_loadModel(JNIEnv *env, jclass, jstring key, jstring yaml) {
    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        service();
        auto k = toString(env, key);
        if (g_models.count(k) == 0) {
            auto options = parseOptionsFromString(toString(env, yaml), /*validate=*/true, /*pathsDir=*/"");
            g_models[k] = std::make_shared<TranslationModel>(options);
        }
    } catch (const std::exception &e) {
        throwJava(env, std::string("Bergamot load failed: ") + e.what());
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_thucnguyen79_aitranslator_spike_mt_BergamotNative_translate(
    JNIEnv *env, jclass, jstring first, jstring second, jobjectArray inputs) {
    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto firstKey = toString(env, first);
        auto secondKey = toString(env, second);
        auto firstModel = g_models.at(firstKey);

        std::vector<std::string> sources;
        jsize n = env->GetArrayLength(inputs);
        for (jsize i = 0; i < n; ++i) {
            auto s = static_cast<jstring>(env->GetObjectArrayElement(inputs, i));
            sources.push_back(toString(env, s));
            env->DeleteLocalRef(s);
        }
        std::vector<ResponseOptions> options(sources.size());

        std::vector<Response> responses = secondKey.empty()
            ? service().translateMultiple(firstModel, std::move(sources), options)
            : service().pivotMultiple(firstModel, g_models.at(secondKey), std::move(sources), options);

        jobjectArray out = env->NewObjectArray(static_cast<jsize>(responses.size()), env->FindClass("java/lang/String"), nullptr);
        for (size_t i = 0; i < responses.size(); ++i) {
            jstring t = env->NewStringUTF(responses[i].target.text.c_str());
            env->SetObjectArrayElement(out, static_cast<jsize>(i), t);
            env->DeleteLocalRef(t);
        }
        return out;
    } catch (const std::exception &e) {
        throwJava(env, std::string("Bergamot translate failed: ") + e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_thucnguyen79_aitranslator_spike_mt_BergamotNative_unloadAll(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_models.clear();
    g_service.reset();
}
