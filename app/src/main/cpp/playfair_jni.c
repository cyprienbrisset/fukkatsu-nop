// JNI bridge to UxPlay's playfair_decrypt (GPL-3.0).
#include <jni.h>
#include <string.h>
#include "playfair.h"

/*
 * FairPlay.nativeDecrypt(keyMsg: ByteArray(164), ekey: ByteArray(72)): ByteArray(16)?
 * keyMsg is the 164-byte body of the second /fp-setup request,
 * ekey the 72-byte encrypted AES key sent in the first SETUP.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_cyprienbrisset_fukkatsunop_airplay_FairPlay_nativeDecrypt(JNIEnv *env, jclass clazz,
                                                                   jbyteArray keyMsg, jbyteArray ekey) {
    (void) clazz;
    if ((*env)->GetArrayLength(env, keyMsg) != 164 || (*env)->GetArrayLength(env, ekey) != 72) return NULL;

    unsigned char msg[164];
    unsigned char cipher[72];
    unsigned char out[16];
    (*env)->GetByteArrayRegion(env, keyMsg, 0, 164, (jbyte *) msg);
    (*env)->GetByteArrayRegion(env, ekey, 0, 72, (jbyte *) cipher);

    playfair_decrypt(msg, cipher, out);

    jbyteArray result = (*env)->NewByteArray(env, 16);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, 16, (const jbyte *) out);
    return result;
}
