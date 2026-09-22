#pragma once

#include <jni.h>

extern "C" {
jbyteArray JNICALL nfgp(JNIEnv* env, jobject thiz, jbyteArray rawInput);

jfloatArray JNICALL nsmc(JNIEnv* env, jobject thiz);
jfloatArray JNICALL nsmg(JNIEnv* env, jobject thiz);
void JNICALL nsmr(JNIEnv* env, jobject thiz);

jfloatArray JNICALL nrmc(JNIEnv* env, jobject thiz);
}
