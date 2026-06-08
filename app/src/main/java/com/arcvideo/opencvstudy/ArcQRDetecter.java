package com.arcvideo.opencvstudy;

import android.graphics.Bitmap;
import android.util.Log;

import com.cv4j.core.datamodel.CV4JImage;
import com.cv4j.core.filters.ConBriFilter;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.king.zxing.DecodeFormatManager;
import com.king.zxing.util.CodeUtils;

import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Symbol;
import com.king.wechat.qrcode.WeChatQRCodeDetector;

import java.util.List;

public class ArcQRDetecter {
    private static final String TAG = "ArcQRDetecter";

    public static final int DECODE_ZXING = 0;
    public static final int DECODE_ZBAR = 1;
    public static final int DECODE_WECHAT = 2;
    public static final int DECODE_OPENCV_DETECT = 3;

    private static ResultPoint[] sortPoints(ResultPoint[] points) {
        ResultPoint[] sorted = new ResultPoint[4];
        float minSum = Float.MAX_VALUE;
        float maxSum = -Float.MAX_VALUE;
        float minDiff = Float.MAX_VALUE;
        float maxDiff = -Float.MAX_VALUE;
        
        ResultPoint tl = null, tr = null, br = null, bl = null;
        for (ResultPoint p : points) {
            float sum = p.getX() + p.getY();
            float diff = p.getY() - p.getX();
            if (sum < minSum) {
                minSum = sum;
                tl = p;
            }
            if (sum > maxSum) {
                maxSum = sum;
                br = p;
            }
            if (diff < minDiff) {
                minDiff = diff;
                tr = p;
            }
            if (diff > maxDiff) {
                maxDiff = diff;
                bl = p;
            }
        }
        sorted[0] = tl;
        sorted[1] = tr;
        sorted[2] = br;
        sorted[3] = bl;
        return sorted;
    }

    public static Bitmap postProcessBitmap(Bitmap bitmapShow,ResultPoint[] resPoints){
        Log.d(TAG,"postProcessBitmap");
        Bitmap qrBitmapShow = null;
        if (resPoints != null){
            try {
                ResultPoint[] sortedPoints = new ResultPoint[4];
                if (resPoints.length == 3) {
                    // ZXing points: 0 is bottom-left, 1 is top-left, 2 is top-right.
                    sortedPoints[0] = resPoints[1]; // top-left
                    sortedPoints[1] = resPoints[2]; // top-right
                    sortedPoints[2] = new ResultPoint(
                        resPoints[0].getX() + resPoints[2].getX() - resPoints[1].getX(),
                        resPoints[0].getY() + resPoints[2].getY() - resPoints[1].getY()
                    ); // bottom-right estimate
                    sortedPoints[3] = resPoints[0]; // bottom-left
                } else if (resPoints.length >= 4) {
                    sortedPoints = sortPoints(resPoints);
                }

                if (sortedPoints[0] != null && sortedPoints[1] != null && sortedPoints[2] != null && sortedPoints[3] != null) {
                    int destSize = 400; // standard rectified size
                    int margin = 40; // white quiet zone margin to improve decoding
                    int actualQrSize = destSize - 2 * margin;

                    org.opencv.core.Mat srcMat = new org.opencv.core.Mat();
                    org.opencv.android.Utils.bitmapToMat(bitmapShow, srcMat);

                    org.opencv.core.Point[] srcPts = new org.opencv.core.Point[4];
                    for (int i = 0; i < 4; i++) {
                        srcPts[i] = new org.opencv.core.Point(sortedPoints[i].getX(), sortedPoints[i].getY());
                    }
                    org.opencv.core.MatOfPoint2f srcPtsMat = new org.opencv.core.MatOfPoint2f(srcPts);

                    org.opencv.core.Point[] dstPts = new org.opencv.core.Point[]{
                        new org.opencv.core.Point(margin, margin),
                        new org.opencv.core.Point(margin + actualQrSize, margin),
                        new org.opencv.core.Point(margin + actualQrSize, margin + actualQrSize),
                        new org.opencv.core.Point(margin, margin + actualQrSize)
                    };
                    org.opencv.core.MatOfPoint2f dstPtsMat = new org.opencv.core.MatOfPoint2f(dstPts);

                    org.opencv.core.Mat perspectiveTransform = org.opencv.imgproc.Imgproc.getPerspectiveTransform(srcPtsMat, dstPtsMat);
                    org.opencv.core.Mat warpedMat = new org.opencv.core.Mat(destSize, destSize, srcMat.type());
                    
                    // Fill with white color for quiet zone
                    warpedMat.setTo(new org.opencv.core.Scalar(255, 255, 255));
                    
                    org.opencv.imgproc.Imgproc.warpPerspective(srcMat, warpedMat, perspectiveTransform, new org.opencv.core.Size(destSize, destSize), org.opencv.imgproc.Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, new org.opencv.core.Scalar(255, 255, 255));

                    Bitmap warpedBitmap = Bitmap.createBitmap(destSize, destSize, Bitmap.Config.ARGB_8888);
                    org.opencv.android.Utils.matToBitmap(warpedMat, warpedBitmap);

                    srcMat.release();
                    warpedMat.release();
                    perspectiveTransform.release();
                    srcPtsMat.release();
                    dstPtsMat.release();

                    qrBitmapShow = imageEnhancement(warpedBitmap);
                    Log.d(TAG, "postProcessBitmap perspective warp successful");
                    return qrBitmapShow;
                }
            } catch (Exception e) {
                Log.e(TAG, "Perspective warp failed, fallback to bounding box crop", e);
            }

            float maxX = 0.0f;
            float minX = 0.0f;
            float maxY = 0.0f;
            float minY = 0.0f;
            for (int i = 0; i < resPoints.length; i ++){
                ResultPoint resultPoint = resPoints[i];
                if (i == 0){
                    maxX = resultPoint.getX();
                    minX = resultPoint.getX();
                    maxY = resultPoint.getY();
                    minY = resultPoint.getY();
                }else{
                    if (maxX < resultPoint.getX()){
                        maxX = resultPoint.getX();
                    }
                    if (minX > resultPoint.getX()){
                        minX = resultPoint.getX();
                    }
                    if (maxY < resultPoint.getY()){
                        maxY = resultPoint.getY();
                    }
                    if (minY > resultPoint.getY()){
                        minY = resultPoint.getY();
                    }
                }
            }

            int qrCodeWidth = (int)(maxX - minX);
            int qrCodeHeight = (int)(maxY - minY);
            int bitmapWidth = bitmapShow.getWidth();
            int bitmapHeight = bitmapShow.getHeight();
            int startX = (int)minX;
            int startY = (int)minY;
            int quarterWith = qrCodeWidth / 4;
            int quarterHeight = qrCodeHeight / 4;

            if (quarterWith > 100){
                if (startX > quarterWith){
                    startX-= quarterWith;
                }else{
                    startX = 0;
                }
            }else{
                if (startX > 100){
                    startX -= 100;
                }else if (startX > 50){
                    startX -= 50;
                }else if (startX > 20){
                    startX -= 20;
                } else{
                    startX = 0;
                }
            }

            if (quarterHeight > 100){
                if (startY > quarterHeight){
                    startY -= quarterHeight;
                }else{
                    startY = 0;
                }
            }else{
                if (startY > 100){
                    startY -= 100;
                }else if (startY > 50){
                    startY -= 50;
                }else if (startY > 20){
                    startY -= 20;
                }
                else{
                    startY = 0;
                }
            }

            int corpWidth = 0;
            int corpHeight = 0;
            corpWidth = (int)(maxX - startX);
            corpHeight = (int)(maxY - startY);
            Log.d(TAG,"postProcessBitmap qrCodeWidth = " + qrCodeWidth + ", qrCodeHeight = " + qrCodeHeight);

            if (quarterWith > 100){
                if ((startX + corpWidth) + quarterWith <= bitmapWidth){
                    corpWidth += quarterWith;
                }else{
                    corpWidth = bitmapWidth - startX;
                }
            }else{
                if (qrCodeWidth >= 100) {
                    if ((startX + corpWidth) + 100 <= bitmapWidth) {
                        corpWidth += 100;
                    } else if ((startX + corpWidth) + 50 <= bitmapWidth) {
                        corpWidth += 50;
                    } else if ((startX + corpWidth) + 20 <= bitmapWidth) {
                        corpWidth += 20;
                    } else {
                        corpWidth = bitmapWidth - startX;
                    }
                }else{
                    if ((startX + corpWidth) + corpWidth <= bitmapWidth){
                        corpWidth = 2*corpWidth;
                    }else{
                        corpWidth = bitmapWidth - startX;
                    }
                }
            }

            if (quarterHeight > 100){
                if ((startY + corpHeight) + quarterHeight <= bitmapHeight){
                    corpHeight += quarterHeight;
                }else{
                    corpHeight = bitmapHeight - startY;
                }
            }else{
                if (qrCodeHeight >= 100){
                    if ((startY + corpHeight) + 100 <= bitmapHeight){
                        corpHeight += 100;
                    }else if ((startY + corpHeight) + 50 <= bitmapHeight){
                        corpHeight += 50;
                    }else if ((startY + corpHeight) + 20 <= bitmapHeight){
                        corpHeight += 20;
                    }else{
                        corpHeight = bitmapHeight - startY;
                    }
                }else{
                    if ((startY + corpHeight) + corpHeight <= bitmapHeight){
                        corpHeight = 2*corpHeight;
                    }else{
                        corpHeight = bitmapHeight - startY;
                    }
                }
            }

            Log.d(TAG,"postProcessBitmap, minX = " + minX + ",maxX = " + maxX + ",minY = " + minY + ", maxY = " + maxY);
            Log.d(TAG,"postProcessBitmap, startX = " + startX + ",startY = " + startY + ",corpWidth = " + corpWidth + ", corpHeight = " + corpHeight);

            Bitmap corpBitmap = Bitmap.createBitmap(bitmapShow,startX,startY,corpWidth,corpHeight);
            qrBitmapShow = imageEnhancement(corpBitmap);
            Log.d(TAG,"postProcessBitmap, bitmap width = " + corpBitmap.getWidth() + ", height = " + corpBitmap.getHeight());
        }
        return qrBitmapShow;
    }

    public static ResultPoint[] detectQRCode(Bitmap bitmap, int decodeType){
        Log.d(TAG,"detectQRCode() in");
        ResultPoint[] resPoints = null;
        boolean findQRCode = false;
        if (bitmap != null){
            bitmap = imageEnhancement(bitmap);
            //showOrigImage(bitmap);
            resPoints = parseQRcode(bitmap, decodeType);
            if(resPoints!= null){
                findQRCode = true;
            }
        }
        Log.d(TAG,"detectQRCode() out");
        return resPoints;
    }

    public static ResultPoint[] parseQRcode(Bitmap bitmap, int decodeType) {
        Log.d(TAG, "parseQRcode in, type=" + decodeType);
        boolean find = false;
        ResultPoint[] resPoints = null;
        
        if (decodeType == DECODE_ZXING) {
            Result rest = CodeUtils.parseCodeResult(bitmap, DecodeFormatManager.QR_CODE_HINTS);
            if (rest != null) {
                resPoints = rest.getResultPoints();
                find = true;
                Log.d(TAG, "parseQRcode out ZXing text = " + rest.getText());
            }
        } else if (decodeType == DECODE_ZBAR) {
            try {
                ImageScanner scanner = new ImageScanner();
                scanner.setConfig(0, Config.X_DENSITY, 3);
                scanner.setConfig(0, Config.Y_DENSITY, 3);
                
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                byte[] barcode = new byte[width * height];
                for (int i = 0; i < pixels.length; i++) {
                    barcode[i] = (byte) (((pixels[i] >> 16) & 0xFF) * 0.299 + ((pixels[i] >> 8) & 0xFF) * 0.587 + (pixels[i] & 0xFF) * 0.114);
                }
                
                Image barcodeImage = new Image(width, height, "Y800");
                barcodeImage.setData(barcode);
                int result = scanner.scanImage(barcodeImage);

                if (result != 0) {
                    SymbolSet syms = scanner.getResults();
                    for (Symbol sym : syms) {
                        String resultStr = sym.getData();
                        if (resultStr != null && !resultStr.isEmpty()) {
                            resPoints = new ResultPoint[] {
                                new ResultPoint(0, 0),
                                new ResultPoint(width, 0),
                                new ResultPoint(width, height),
                                new ResultPoint(0, height)
                            };
                            Log.d(TAG, "parseQRcode out ZBar text = " + resultStr);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "ZBar error", e);
            }
        } else if (decodeType == DECODE_WECHAT) {
            try {
                // WeChatQRCodeDetector returns list of results
                List<String> results = WeChatQRCodeDetector.detectAndDecode(bitmap);
                if (results != null && !results.isEmpty()) {
                    // Similar to ZBar, WeChatQRCodeDetector might not return points in the easy wrapper
                    // We just use corners for the bounding box.
                    resPoints = new ResultPoint[] {
                        new ResultPoint(0, 0),
                        new ResultPoint(bitmap.getWidth(), 0),
                        new ResultPoint(bitmap.getWidth(), bitmap.getHeight()),
                        new ResultPoint(0, bitmap.getHeight())
                    };
                    Log.d(TAG, "parseQRcode out WeChat text = " + results.get(0));
                }
            } catch (Exception e) {
                Log.e(TAG, "WeChat error", e);
            }
        } else if (decodeType == DECODE_OPENCV_DETECT) {
            try {
                org.opencv.core.Mat img = new org.opencv.core.Mat();
                org.opencv.android.Utils.bitmapToMat(bitmap, img);
                org.opencv.core.Mat gray = new org.opencv.core.Mat();
                org.opencv.imgproc.Imgproc.cvtColor(img, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);

                org.opencv.objdetect.QRCodeDetector detector = new org.opencv.objdetect.QRCodeDetector();
                org.opencv.core.Mat points = new org.opencv.core.Mat();
                boolean found = detector.detect(gray, points);

                if (found && !points.empty()) {
                    int total = (int) (points.total() * points.channels());
                    if (total >= 8) {
                        float[] data = new float[total];
                        points.get(0, 0, data);
                        resPoints = new ResultPoint[4];
                        for (int i = 0; i < 4; i++) {
                            resPoints[i] = new ResultPoint(data[i*2], data[i*2 + 1]);
                        }
                        Log.d(TAG, "parseQRcode out OpenCV detect successful");
                    }
                }

                img.release();
                gray.release();
                points.release();
            } catch (Exception e) {
                Log.e(TAG, "OpenCV Detect error", e);
            }
        }

        if (resPoints != null) {
            for (int i = 0; i < resPoints.length; i++) {
                Log.d(TAG, "parseQRcode out points[" + i + "] = [x = " + resPoints[i].getX() + ", y = " + resPoints[i].getY() + "]");
            }
        }

        Log.d(TAG, "parseQRcode out");
        return resPoints;
    }

    public static Object[] testAllDecodersAndGenerateQR(Bitmap bitmap) {
        StringBuilder resultStr = new StringBuilder();
        Bitmap zxingQr = null;
        Bitmap zbarQr = null;
        Bitmap wechatQr = null;
        String zxingText = null;
        String zbarText = null;
        String wechatText = null;
        int qrSize = 400;
        
        // 1. ZXing
        long start = System.currentTimeMillis();
        boolean zxingSuccess = false;
        long zxingGenTime = 0;
        try {
            Result rest = CodeUtils.parseCodeResult(bitmap, DecodeFormatManager.QR_CODE_HINTS);
            if (rest != null && rest.getText() != null && !rest.getText().isEmpty()) {
                zxingSuccess = true;
                zxingText = rest.getText();
            }
        } catch (Exception e) {
        }
        long zxingTime = System.currentTimeMillis() - start;
        
        if (zxingSuccess) {
            long genStart = System.currentTimeMillis();
            try {
                zxingQr = CodeUtils.createQRCode(zxingText, qrSize, null);
            } catch (Exception e) {}
            zxingGenTime = System.currentTimeMillis() - genStart;
            resultStr.append("ZXing: 成功 (解码:").append(zxingTime).append("ms, 生成:").append(zxingGenTime).append("ms)\n");
        } else {
            resultStr.append("ZXing: 失败 (").append(zxingTime).append("ms)\n");
        }

        // 2. ZBar
        start = System.currentTimeMillis();
        boolean zbarSuccess = false;
        long zbarGenTime = 0;
        try {
            ImageScanner scanner = new ImageScanner();
            scanner.setConfig(0, Config.X_DENSITY, 3);
            scanner.setConfig(0, Config.Y_DENSITY, 3);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            byte[] barcode = new byte[width * height];
            for (int i = 0; i < pixels.length; i++) {
                barcode[i] = (byte) (((pixels[i] >> 16) & 0xFF) * 0.299 + ((pixels[i] >> 8) & 0xFF) * 0.587 + (pixels[i] & 0xFF) * 0.114);
            }
            Image barcodeImage = new Image(width, height, "Y800");
            barcodeImage.setData(barcode);
            int result = scanner.scanImage(barcodeImage);
            if (result != 0) {
                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    String data = sym.getData();
                    if (data != null && !data.isEmpty()) {
                        zbarSuccess = true;
                        zbarText = data;
                        break;
                    }
                }
            }
        } catch (Exception e) {
        }
        long zbarTime = System.currentTimeMillis() - start;

        if (zbarSuccess) {
            long genStart = System.currentTimeMillis();
            try {
                zbarQr = CodeUtils.createQRCode(zbarText, qrSize, null);
            } catch (Exception e) {}
            zbarGenTime = System.currentTimeMillis() - genStart;
            resultStr.append("ZBar: 成功 (解码:").append(zbarTime).append("ms, 生成:").append(zbarGenTime).append("ms)\n");
        } else {
            resultStr.append("ZBar: 失败 (").append(zbarTime).append("ms)\n");
        }

        // 3. WeChat
        start = System.currentTimeMillis();
        boolean wechatSuccess = false;
        long wechatGenTime = 0;
        try {
            List<String> results = WeChatQRCodeDetector.detectAndDecode(bitmap);
            if (results != null && !results.isEmpty()) {
                wechatSuccess = true;
                wechatText = results.get(0);
            }
        } catch (Exception e) {
        }
        long wechatTime = System.currentTimeMillis() - start;

        if (wechatSuccess) {
            long genStart = System.currentTimeMillis();
            try {
                wechatQr = CodeUtils.createQRCode(wechatText, qrSize, null);
            } catch (Exception e) {}
            wechatGenTime = System.currentTimeMillis() - genStart;
            resultStr.append("WeChat: 成功 (解码:").append(wechatTime).append("ms, 生成:").append(wechatGenTime).append("ms)");
        } else {
            resultStr.append("WeChat: 失败 (").append(wechatTime).append("ms)");
        }

        if (zxingText != null) {
            resultStr.append("\nZXing内容: ").append(zxingText);
        }
        if (zbarText != null && !zbarText.equals(zxingText)) {
            resultStr.append("\nZBar内容: ").append(zbarText);
        }
        if (wechatText != null && !wechatText.equals(zxingText) && !wechatText.equals(zbarText)) {
            resultStr.append("\nWeChat内容: ").append(wechatText);
        }

        return new Object[] {resultStr.toString(), zxingQr, zbarQr, wechatQr};
    }


    public static Bitmap imageEnhancement(Bitmap bitmap){
        Log.d(TAG,"imageEnhancement in");
        Bitmap curBitmap = null;
        // to do enhancement
        CV4JImage cv4JImage = new CV4JImage(bitmap);

        //MedimaFilter去噪声
        //MedimaFilter medimaFilter = new MedimaFilter();
        //medimaFilter.filter(cv4JImage.getProcessor());

        //提升对比度
        ConBriFilter conBriFilter = new ConBriFilter();
        conBriFilter.setBrightness(1.2f);
        conBriFilter.setContrast(1.8f);
        conBriFilter.filter(cv4JImage.getProcessor());

        //锐化: 摄像头噪点多的情况下锐化后会降低识别率
        //SharpFilter sharpFilter = new SharpFilter();
        //sharpFilter.filter(cv4JImage.getProcessor());

        //Threshold变成黑白图，增加二维码识别率(实测背光情况下效果不行)
        //Threshold threshold = new Threshold();
        //threshold.process((ByteProcessor)(cv4JImage.convert2Gray().getProcessor()),Threshold.THRESH_OTSU,Threshold.METHOD_THRESH_BINARY,255);
        Bitmap newBitmap = cv4JImage.getProcessor().getImage().toBitmap(Bitmap.Config.ARGB_8888);

        curBitmap = newBitmap;
        Log.d(TAG,"imageEnhancement out");
        return curBitmap;
    }
}
