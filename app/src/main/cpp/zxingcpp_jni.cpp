#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "ZXing/ReadBarcode.h"
#include "ZXing/ImageView.h"

#define LOG_TAG "ZXingCppJni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace ZXing;

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_arcvideo_opencvstudy_ZXingCppDetector_detectAndDecode(JNIEnv *env, jclass clazz, jobject bitmap) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
        return nullptr;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888");
        return nullptr;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("AndroidBitmap_lockPixels failed");
        return nullptr;
    }
    
    ImageView image(static_cast<uint8_t*>(pixels), info.width, info.height, ImageFormat::RGBA, info.stride);
    
    ReaderOptions options;
    options.setFormats(BarcodeFormat::QRCode);
    options.setTryHarder(true);
    options.setTryRotate(true);
    options.setTryInvert(true);
    
    Barcode barcode = ReadBarcode(image, options);
    
    AndroidBitmap_unlockPixels(env, bitmap);
    
    if (!barcode.isValid()) {
        LOGD("No valid barcode found");
        return nullptr;
    }
    
    std::string text = barcode.text();
    auto pos = barcode.position();
    
    // Create Object[] array of size 2
    jclass objectClass = env->FindClass("java/lang/Object");
    jobjectArray result = env->NewObjectArray(2, objectClass, nullptr);
    
    jstring jText = env->NewStringUTF(text.c_str());
    env->SetObjectArrayElement(result, 0, jText);
    
    // Create float array for points: [x0, y0, x1, y1, x2, y2, x3, y3]
    jfloatArray jPoints = env->NewFloatArray(8);
    float points[8];
    points[0] = static_cast<float>(pos.topLeft().x);
    points[1] = static_cast<float>(pos.topLeft().y);
    points[2] = static_cast<float>(pos.topRight().x);
    points[3] = static_cast<float>(pos.topRight().y);
    points[4] = static_cast<float>(pos.bottomRight().x);
    points[5] = static_cast<float>(pos.bottomRight().y);
    points[6] = static_cast<float>(pos.bottomLeft().x);
    points[7] = static_cast<float>(pos.bottomLeft().y);
    
    env->SetFloatArrayRegion(jPoints, 0, 8, points);
    env->SetObjectArrayElement(result, 1, jPoints);
    
    return result;
}
