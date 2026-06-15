package com.arcvideo.opencvstudy;

import android.graphics.Bitmap;

public class ZXingCppDetector {

    static {
        System.loadLibrary("zxingcpp_wrapper");
    }

    /**
     * Detects and decodes a QR code from a Bitmap using zxing-cpp.
     * 
     * @param bitmap The image to scan.
     * @return An Object array where:
     *         index 0: The decoded String (if successful), or null if none found.
     *         index 1: A float[] containing 8 values representing the 4 corner points (x0, y0, x1, y1, x2, y2, x3, y3).
     */
    public static native Object[] detectAndDecode(Bitmap bitmap);
}
