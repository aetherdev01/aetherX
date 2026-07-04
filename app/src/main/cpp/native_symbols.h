// native_symbols.h
//
// Deklarasi fungsi native dengan nama PENDEK (lihat sigcheck.cpp &
// integrityguard.cpp untuk definisinya) yang didaftarkan manual sebagai JNI
// native method lewat RegisterNatives() di jni_onload.cpp — bukan lewat
// konvensi penamaan panjang `Java_com_aether_x_..._namaMethod` yang dicari
// otomatis oleh JVM.
//
// Header ini jadi SATU-SATUNYA tempat yang perlu diketahui isi/alamat
// fungsi-fungsi ini oleh file .cpp lain (jni_onload.cpp untuk didaftarkan,
// integrityguard.cpp untuk membaca byte code-nya) — supaya nama pendek ini
// tidak perlu di-deklarasi ulang berkali-kali di banyak tempat.
#pragma once

#include <jni.h>

extern "C" {

// sigcheck.cpp — lihat file itu untuk penjelasan lengkap logikanya.
JNIEXPORT jboolean JNICALL nvfy(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);
JNIEXPORT jboolean JNICALL nvfy2(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);

// integrityguard.cpp — lihat file itu untuk penjelasan lengkap logikanya.
JNIEXPORT jint JNICALL nvint(JNIEnv* env, jobject thiz);

}  // extern "C"
