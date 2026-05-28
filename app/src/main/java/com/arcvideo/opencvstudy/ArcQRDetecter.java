package com.arcvideo.opencvstudy;

import android.graphics.Bitmap;
import android.util.Log;

import com.cv4j.core.datamodel.CV4JImage;
import com.cv4j.core.filters.ConBriFilter;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.king.zxing.DecodeFormatManager;
import com.king.zxing.util.CodeUtils;

public class ArcQRDetecter {
    private static final String TAG = "ArcQRDetecter";

    public static Bitmap postProcessBitmap(Bitmap bitmapShow,ResultPoint[] resPoints){
        Log.d(TAG,"postProcessBitmap");
        Bitmap qrBitmapShow = null;
        if (resPoints != null){
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
            //to do image enhance

        }
        return qrBitmapShow;
    }

    public static ResultPoint[] detectQRCode(Bitmap bitmap){
        Log.d(TAG,"detectQRCode() in");
        ResultPoint[] resPoints = null;
        boolean findQRCode = false;
        if (bitmap != null){
            bitmap = imageEnhancement(bitmap);
            //showOrigImage(bitmap);
            resPoints = parseQRcode(bitmap);
            if(resPoints!= null){
                findQRCode = true;
            }
        }
        Log.d(TAG,"detectQRCode() out");
        return resPoints;
    }

    public static ResultPoint[] parseQRcode(Bitmap bitmap) {
        Log.d(TAG, "parseQRcode in");
        boolean find = false;
        ResultPoint[] resPoints = null;
        Result rest = CodeUtils.parseCodeResult(bitmap, DecodeFormatManager.QR_CODE_HINTS);
        if (rest != null) {
            resPoints = rest.getResultPoints();
            for (int i = 0; i < resPoints.length; i++) {
                Log.d(TAG, "parseQRcode out points[" + i + "] = " + "[x = " + resPoints[i].getX() + ", y = " + resPoints[i].getY() + "]");
            }
            Log.d(TAG, "parseQRcode out test = " + rest.getText());
            find = true;
            //resPoints = points;
        }
        Log.d(TAG, "parseQRcode out");
        return resPoints;
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
