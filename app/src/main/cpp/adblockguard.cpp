#include <jni.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <cstring>
#include <strings.h>

#include "native_symbols.h"

namespace {

constexpr const char* kVpnInterfacePrefixes[] = {"tun", "ppp"};
constexpr size_t kVpnInterfacePrefixCount =
    sizeof(kVpnInterfacePrefixes) / sizeof(kVpnInterfacePrefixes[0]);

bool isVpnLikeInterfaceName(const char* name) {
    for (size_t i = 0; i < kVpnInterfacePrefixCount; i++) {
        const size_t prefixLen = strlen(kVpnInterfacePrefixes[i]);
        if (strncmp(name, kVpnInterfacePrefixes[i], prefixLen) == 0) {
            return true;
        }
    }
    return false;
}

bool detectVpnInterface() {
    ifaddrs* addrs = nullptr;
    if (getifaddrs(&addrs) != 0) {
        return false;
    }

    bool found = false;
    for (ifaddrs* it = addrs; it != nullptr; it = it->ifa_next) {
        if (it->ifa_name == nullptr) continue;
        if (it->ifa_flags != 0 && !(it->ifa_flags & IFF_UP)) continue;
        if (isVpnLikeInterfaceName(it->ifa_name)) {
            found = true;
            break;
        }
    }

    freeifaddrs(addrs);
    return found;
}

constexpr const char* kAdBlockDnsIps[] = {
    "94.140.14.14", "94.140.15.15",
    "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
    "94.140.14.15", "94.140.15.16",
    "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff",

    "194.242.2.3",
    "194.242.2.4",
    "194.242.2.5",
    "194.242.2.6",
    "194.242.2.9",
    "2a07:e340::3", "2a07:e340::4",
};
constexpr size_t kAdBlockDnsIpCount = sizeof(kAdBlockDnsIps) / sizeof(kAdBlockDnsIps[0]);

bool isKnownAdBlockDnsIp(const char* ip) {
    for (size_t i = 0; i < kAdBlockDnsIpCount; i++) {
        if (strcmp(ip, kAdBlockDnsIps[i]) == 0) return true;
    }
    return false;
}

constexpr const char* kAdBlockDnsHostnames[] = {
    "dns.adguard.com",
    "dns-family.adguard.com",

    "adblock.dns.mullvad.net",
    "base.dns.mullvad.net",
    "extended.dns.mullvad.net",
    "family.dns.mullvad.net",
    "all.dns.mullvad.net",
};
constexpr size_t kAdBlockDnsHostnameCount =
    sizeof(kAdBlockDnsHostnames) / sizeof(kAdBlockDnsHostnames[0]);

bool isKnownAdBlockDnsHostname(const char* hostname) {
    for (size_t i = 0; i < kAdBlockDnsHostnameCount; i++) {
        if (strcasecmp(hostname, kAdBlockDnsHostnames[i]) == 0) return true;
    }
    return false;
}

constexpr const char* kAdBlockModuleKeywords[] = {
    "adaway",
    "systemless-hosts",
    "systemlesshosts",
    "adblock",
    "ad-block",
    "ad_block",
    "youtube_adaway",
    "noads",
    "no-ads",
    "no_ads",
};
constexpr size_t kAdBlockModuleKeywordCount =
    sizeof(kAdBlockModuleKeywords) / sizeof(kAdBlockModuleKeywords[0]);

bool containsKnownAdBlockModuleKeyword(const char* listing) {
    for (size_t i = 0; i < kAdBlockModuleKeywordCount; i++) {
        if (strcasestr(listing, kAdBlockModuleKeywords[i]) != nullptr) {
            return true;
        }
    }
    return false;
}

}

extern "C" JNIEXPORT jboolean JNICALL
nvpn(JNIEnv* /* env */, jobject /* thiz */) {
    return detectVpnInterface() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
ndns(JNIEnv* env, jobject /* thiz */, jobjectArray dnsServers) {
    if (dnsServers == nullptr) return JNI_FALSE;

    const jsize count = env->GetArrayLength(dnsServers);
    for (jsize i = 0; i < count; i++) {
        auto element = static_cast<jstring>(env->GetObjectArrayElement(dnsServers, i));
        if (element == nullptr) continue;

        const char* ip = env->GetStringUTFChars(element, nullptr);
        const bool match = (ip != nullptr) &&
            (isKnownAdBlockDnsIp(ip) || isKnownAdBlockDnsHostname(ip));
        if (ip != nullptr) env->ReleaseStringUTFChars(element, ip);
        env->DeleteLocalRef(element);

        if (match) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
nmod(JNIEnv* env, jobject /* thiz */, jstring moduleListing) {
    if (moduleListing == nullptr) return JNI_FALSE;

    const char* listing = env->GetStringUTFChars(moduleListing, nullptr);
    if (listing == nullptr) return JNI_FALSE;

    const bool match = containsKnownAdBlockModuleKeyword(listing);
    env->ReleaseStringUTFChars(moduleListing, listing);
    return match ? JNI_TRUE : JNI_FALSE;
}
