package com.arcvideo.opencvstudy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.zxing.ResultPoint;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements Handler.Callback,ArcFrameDataCallBack,SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    /**
     * 默认Camera参数
     */
    private int mPreviewWidth = 1920;
    private int mPreviewHeight = 1080;
    private int mPreviewFPS = 30;
    private int mScreenWidth = 1920;
    private int mScreenHeight = 1080;
    private ArcCamera2 mArcCamera2 = null;
    protected Handler mRefreshHandler = new Handler(Looper.getMainLooper(), this);

    private static final int HANDLER_MSG_START_CAMERA = 1000;
    private static final int HANDLER_MSG_CAMERA_START_PREVIEW = 1010;
    private static final int HANDLER_MSG_IMAGE_SHOW = 1020;
    private static final int HANDLER_MSG_IMAGE_POSTPROCESS = 1030;
    private static final int HANDLER_MSG_DETECT_QRCODE = 1040;
    private static final int HANDLER_MSG_RESIZE_SURFACEVIEW = 1050;
    private static final int HANDLER_MSG_IMAGE_SHOW_DIALOG = 1060;

    private Button btnStart;
    private Button btnRefresh;
    private int frameCount = 0;
    private boolean findQRCode = false;
    private Display mWindowDisplay = null;
    private ImageView imgView = null;
    private ImageView imgViewOrg = null;
    private SurfaceView surfaceView = null;
    private SurfaceHolder surfaceHolder = null;
    private boolean bSurfaceCreated = false;
    private Bitmap bitmapShow = null;
    private Bitmap qrBitmapShow = null;
    private int cameraDataRotate = 0;
    ResultPoint[] resPoints = null;
    ArcVFrame mCurrentVFrame = null;
    Object mVFrameLock = new Object();
    private int defaultButtonTextColor = Color.BLACK;
    private boolean bFirstFrame = true;
    private LinearLayout linearLayoutSur = null;

    /**
     * android 6.0 或以上权限申请
     */
    private static final int REQUEST_CODE = 0;						//请求码
    private CheckPermission mCheckPermission;						//检测权限工具
    private boolean mIsPermissionGanted = false;					//是否授权成功

    //配置需要取的权限
    static final String[] PERMISSION = new String[] {
            Manifest.permission.CAMERA,								// 摄像头权限
            Manifest.permission.WRITE_EXTERNAL_STORAGE,				// SD卡写入权限
            Manifest.permission.READ_EXTERNAL_STORAGE				// SD卡读取权限
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        loadNatvieLibs();
        btnStart = findViewById(R.id.start1);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"onClick start button");
                if (mArcCamera2 == null) {
                    mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_START_CAMERA,500);
                    updateRefreshButtonStatus(false);
                }
            }
        });

        btnRefresh = findViewById(R.id.refresh);
        defaultButtonTextColor = btnRefresh.getCurrentTextColor();
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"onClick refresh button");
                if (findQRCode){
                    clearQRCodeImageView();
                }
                findQRCode = false;
                updateRefreshButtonStatus(false);
            }
        });

        surfaceView = findViewById(R.id.previewSurface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        bSurfaceCreated = false;

        findQRCode = false;
        linearLayoutSur = findViewById(R.id.linLayoutSur);

        mWindowDisplay = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        initPermission();

        WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenWidth =  metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        Log.d(TAG,"mScreenWidth = " + mScreenWidth + ", mScreenHeight = " + mScreenHeight);
    }

    private void initPermission() {
        //SDK版本小于23时候不做检测
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;

        mCheckPermission = new CheckPermission(this);
        //缺少权限时，进入权限设置页面
        if (mCheckPermission.permissionSet(PERMISSION)) {
            startPermissionActivity();
        } else {
            mIsPermissionGanted = true;
        }

    }

    //进入权限设置页面
    private void startPermissionActivity() {
        PermissionActivity.startActivityForResult(this, REQUEST_CODE, PERMISSION);
    }

    //返回结果回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //拒绝时，没有获取到主要权限，无法运行，关闭页面
        if (requestCode == REQUEST_CODE) {
            if (resultCode == PermissionActivity.PERMISSION_DENIEG) {
                finish();
            }else{
                mIsPermissionGanted = true;
            }
        }
    }

    @Override
    public void onBackPressed(){
        finish();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy");
        super.onDestroy();
        if (mArcCamera2 != null) {
            mArcCamera2.releaseCamera();
            mArcCamera2 = null;
        }
        bFirstFrame = true;
        bitmapShow = null;
        qrBitmapShow = null;
        findQRCode = false;
    }

    public void startCamera(){
        Log.d(TAG,"startCamera");
        if (mArcCamera2 != null) {
            return;
        }
        int res = 0;
        mArcCamera2 = ArcCamera2.getInstance(this);
        mArcCamera2.setCameraFacingType(ArcTypes.CAMERA_FACING_BACK);
        res = mArcCamera2.openCamera(mPreviewWidth, mPreviewHeight, mPreviewFPS);
        mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_CAMERA_START_PREVIEW, 200);
    }

    private void startArcCameraPreview() {
        Log.d(TAG,"startArcCamera2Preview");
        if (mArcCamera2.isOpened()) {
            mArcCamera2.setCameraDisplayOrientation(getCameraOrientation(mWindowDisplay));
            mArcCamera2.setCameraDataCallBack((ArcFrameDataCallBack) this);
            if (bSurfaceCreated){
                mArcCamera2.setPreviewSurfaceHolder(surfaceHolder);
            }
            mArcCamera2.openPreview(getCameraOrientation(mWindowDisplay));
        } else {
            mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_CAMERA_START_PREVIEW, 200);
        }
    }

    private int getCameraOrientation(Display display){
        int cameraOrientation = 0;
        if (display != null) {
            //getRotation() Add in android api level 8
            int rotate = display.getRotation();
            switch (rotate) {
                case Surface.ROTATION_0: cameraOrientation = 0; break;
                case Surface.ROTATION_90: cameraOrientation = 90; break;
                case Surface.ROTATION_180: cameraOrientation = 180; break;
                case Surface.ROTATION_270: cameraOrientation = 270; break;
            }
        }

        return cameraOrientation;
    }
    @Override
    public boolean handleMessage(@NonNull Message message) {
        switch (message.what) {
            case HANDLER_MSG_START_CAMERA:
                startCamera();
                break;
            case HANDLER_MSG_CAMERA_START_PREVIEW:
                startArcCameraPreview();
                break;
            case HANDLER_MSG_IMAGE_POSTPROCESS:
                qrBitmapShow = ArcQRDetecter.postProcessBitmap(bitmapShow,resPoints);
                mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_IMAGE_SHOW_DIALOG,30);
                break;
            case HANDLER_MSG_DETECT_QRCODE:
                detectQRCode();
                break;
            case HANDLER_MSG_IMAGE_SHOW:
                showQRCodeCropImage(qrBitmapShow);
                break;
            case HANDLER_MSG_RESIZE_SURFACEVIEW:
                adjustSurfaceViewSize(mCurrentVFrame.mFrameWidth,mCurrentVFrame.mFrameHeight);
                break;
            case HANDLER_MSG_IMAGE_SHOW_DIALOG:
                showDialogImage(qrBitmapShow);
                break;
            default:
                break;
        }
        return false;
    }

    private void clearQRCodeImageView(){
        if (imgView != null){
            imgView.setImageDrawable(null);
        }
    }
    private void showQRCodeCropImage(Bitmap bitmap){
        Log.d(TAG,"showQRCodeCropImage in");
        Mat src = new Mat(bitmap.getWidth(),bitmap.getHeight(), CvType.CV_8UC(3));
        Utils.bitmapToMat(bitmap,src);
        Imgproc.cvtColor(src,src,Imgproc.COLOR_BGR2GRAY);
        Utils.matToBitmap(src,bitmap);
        Log.d(TAG,"showQRCodeCropImage 1111");

        if (imgView == null){
            imgView = findViewById(R.id.imageView);
            imgView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (bitmap != null){
            imgView.setRotation(cameraDataRotate);
            imgView.setImageBitmap(bitmap);
        }

        //updateRefreshButtonStatus(true);
        Log.d(TAG,"showQRCodeCropImage out");
    }

    private void showSelectedImage(Bitmap bitmap){
        Bitmap outBitmap = adaptiveBrightnessAdjustment(bitmap);

        if (imgView == null){
            imgView = findViewById(R.id.imageView);
            imgView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (bitmap != null){
            imgView.setRotation(cameraDataRotate);
            imgView.setImageBitmap(bitmap);
        }
    }

    private void showOrigImage(Bitmap bitmap){
        Log.d(TAG,"showOrigImage in");
        if (imgViewOrg == null){
            imgViewOrg = findViewById(R.id.imageView0);
            imgViewOrg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (bitmap != null){
            imgViewOrg.setImageBitmap(bitmap);
        }
        Log.d(TAG,"showOrigImage out");
    }

    @Override
    public void onFrame(ArcVFrame vFrame) {
        Log.d(TAG,"onFrame width = " +  vFrame.mFrameWidth + ", height = " + vFrame.mFrameHeight);
        if (frameCount % 30 == 0 && !findQRCode){
            synchronized (mVFrameLock){
                mCurrentVFrame = vFrame;
                if (bFirstFrame){
                    bFirstFrame = false;
                    //mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_RESIZE_SURFACEVIEW,10);
                }
                mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_DETECT_QRCODE,10);
            }
        }
        frameCount++;
    }

    public void detectQRCode(){
        Log.d(TAG,"detectQRCode() in");
        Bitmap bitmap = null;
        synchronized (mVFrameLock){
            if (mCurrentVFrame != null){
                bitmap = YuvToBitmap(mCurrentVFrame);
                cameraDataRotate = mCurrentVFrame.mRotation;
            }else{
                return;
            }
        }

        if (bitmap != null){
            showSelectedImage(bitmap);
            bitmap = ArcQRDetecter.imageEnhancement(bitmap);
            //showOrigImage(bitmap);
            resPoints = ArcQRDetecter.parseQRcode(bitmap);
            if(resPoints != null){
                bitmapShow = bitmap;
                mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_IMAGE_POSTPROCESS,10);
                findQRCode = true;
            }
        }
        Log.d(TAG,"detectQRCode() out");
    }

    public Bitmap YuvToBitmap(ArcVFrame vFrame){
        Bitmap bitmap = null;
        Log.d(TAG,"YuvToBitmap in");
        int format = ImageFormat.YUV_420_888;
        if (vFrame.mColorFormat == ArcTypes.ARC_PIXELFORMAT_I420){
            format = ImageFormat.YUV_420_888;
        }else if (vFrame.mColorFormat == ArcTypes.ARC_PIXELFORMAT_NV21){
            format = ImageFormat.NV21;
        }
        Log.d(TAG,"YuvToBitmap format = " + format);
        // 创建YuvImage对象
        YuvImage yuvImage = new YuvImage(vFrame.mFrameBuffer, format, vFrame.mFrameWidth, vFrame.mFrameHeight, null);

        // 将YUV数据转换为Bitmap
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (yuvImage.compressToJpeg(new Rect(0, 0, vFrame.mFrameWidth, vFrame.mFrameHeight), 100, out)) {
            byte[] imageBytes = out.toByteArray();
            bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }
        Log.d(TAG,"YuvToBitmap out ");
        return bitmap;
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        Log.d(TAG,"surfaceCreated");
        bSurfaceCreated = true;
        surfaceView.setZOrderOnTop(true);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d(TAG,"surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        Log.d(TAG,"surfaceDestroyed");
        bSurfaceCreated = false;
        mArcCamera2.releaseCamera();
        mArcCamera2 = null;
        bFirstFrame = true;
        findQRCode = false;
    }

    private void updateRefreshButtonStatus(boolean bNeedRefresh){
        if (bNeedRefresh){
            btnRefresh.setText("刷新");
            btnRefresh.setTextColor(defaultButtonTextColor);
        }else{
            btnRefresh.setText("检测中...");
            btnRefresh.setTextColor(Color.RED);
        }
    }

    protected int mDisplayType = 1;
    private void adjustSurfaceViewSize(int imageWidth,int imageHeight){
        int layoutX = 0;
        int layoutY = 0;
        int layoutWidth = 0;
        int layoutHeight = 0;

        int m_frameWidth = imageWidth;
        int m_frameHeight = imageHeight;
        int nScreenWidth = linearLayoutSur.getMeasuredWidth();
        int nScreenHeight = linearLayoutSur.getMeasuredHeight();
        Log.v(TAG, "adjustSurfaceViewSize nScreenWidth = " + nScreenWidth + ", nScreenHeight = "
                + nScreenHeight);

        float aspect_ratio = 0;
        Log.v(TAG, "adjustSurfaceViewSize before adjuct aspect, m_frameWidth = " + m_frameWidth + ", m_frameHeight = "
                + m_frameHeight);

        if (aspect_ratio > 0.1f) {
            m_frameWidth = Float.floatToIntBits((Float.intBitsToFloat(m_frameHeight) * aspect_ratio));
        }
        /* start */
        if (m_frameWidth != 0 && m_frameHeight != 0) {
            int estimateW, estimateH;
            switch (mDisplayType) {
                case 0:
                    if (nScreenWidth * m_frameHeight > nScreenHeight * m_frameWidth) {
                        estimateW = nScreenHeight * m_frameWidth / m_frameHeight;
                        estimateH = nScreenHeight;
                        if (estimateW % 4 != 0)
                            estimateW -= estimateW % 4;
                    } else {
                        estimateW = nScreenWidth;
                        estimateH = nScreenWidth * m_frameHeight / m_frameWidth;
                        if (estimateH % 4 != 0)
                            estimateH -= estimateH % 4;
                    }
                    break;
                case 1:
                    if (nScreenWidth * m_frameHeight > nScreenHeight * m_frameWidth) {
                        estimateW = nScreenWidth;
                        estimateH = nScreenWidth * m_frameHeight / m_frameWidth;
                        if (estimateH % 4 != 0)
                            estimateH -= estimateH % 4;
                    } else {

                        estimateW = nScreenHeight * m_frameWidth / m_frameHeight;
                        estimateH = nScreenHeight;
                        if (estimateW % 4 != 0)
                            estimateW -= estimateW % 4;
                    }
                    break;
                default:
                    estimateW = nScreenWidth;
                    estimateH = nScreenHeight;
            }
            int xOffset = (nScreenWidth - estimateW) / 2;
            int yOffset = (nScreenHeight - estimateH) / 2;
            if (xOffset % 4 != 0)
                xOffset -= xOffset % 4;
            if (yOffset % 4 != 0)
                yOffset -= yOffset % 4;
            Log.d(TAG, xOffset + ", " + yOffset + ", " + estimateW + "x"
                    + estimateH);

            layoutX = xOffset;
            layoutY = yOffset;
            layoutWidth = estimateW;
            layoutHeight = estimateH;
        } else {
            layoutWidth = nScreenWidth;
            layoutHeight = nScreenHeight;
        }

        Log.v(TAG, "adjustSurfaceViewSize layoutX = " + layoutX + ", layoutY = " + layoutY + ", layoutWidth = " + layoutWidth + ", layoutHeight = " + layoutHeight);
        FrameLayout.LayoutParams lp;
        lp = (FrameLayout.LayoutParams) (surfaceView.getLayoutParams());

        lp.topMargin = layoutY;
        lp.leftMargin = layoutX;
        lp.width = layoutWidth;
        lp.height = layoutHeight;

        surfaceView.setLayoutParams(lp);
    }

    private void showDialogImage(Bitmap bitmap){
        if (bitmap == null){
            return;
        }

        updateRefreshButtonStatus(true);

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.activity_dialog);
        WindowManager.LayoutParams windLayout = dialog.getWindow().getAttributes();
        windLayout.height = mScreenHeight;
        windLayout.width = mScreenHeight;
        dialog.getWindow().setAttributes(windLayout);
        dialog.setCanceledOnTouchOutside(false);

        ImageView imageView = dialog.findViewById(R.id.imageV);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(bitmap);

        Button btnClose = dialog.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRefreshHandler.sendEmptyMessageDelayed(HANDLER_MSG_IMAGE_SHOW,10);
                dialog.cancel();
            }
        });
        dialog.show();
    }

    private void loadNatvieLibs(){
        try {
            System.loadLibrary("opencv_java4");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private Bitmap adaptiveBrightnessAdjustment(Bitmap inBitmap) {
        Log.d(TAG,"adaptiveBrightnessAdjustment in");
        Mat rgbMt = new Mat(inBitmap.getWidth(),inBitmap.getHeight(), CvType.CV_8UC(3));
        Mat yuvMt = new Mat();
        Mat outputImageMt = new Mat();
        Utils.bitmapToMat(inBitmap,rgbMt);
        Log.d(TAG,"adaptiveBrightnessAdjustment bitmapToMat");
        // 转换为YUV
        Imgproc.cvtColor(rgbMt, yuvMt, Imgproc.COLOR_RGB2YUV);

        Log.d(TAG,"adaptiveBrightnessAdjustment COLOR_RGB2YUV");

        // 分离YUV通道
        List<Mat> yuvChannels = new ArrayList<>();
        Core.split(yuvMt, yuvChannels);
        Mat yChannel = yuvChannels.get(0); // Y通道

        // 计算图像的平均亮度
        double meanBrightness = Core.mean(yChannel).val[0];

        Log.d(TAG,"adaptiveBrightnessAdjustment meanBrightness = " + meanBrightness);
        // 根据平均亮度调整图像
        if (meanBrightness < 50) { // 夜晚环境
            // 使用自适应直方图均衡化
            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(yChannel, yChannel);
            Log.d(TAG,"adaptiveBrightnessAdjustment CLAHE ");
        } else { // 白天环境
            // 使用全局直方图均衡化
            Imgproc.equalizeHist(yChannel, yChannel);
            Log.d(TAG,"adaptiveBrightnessAdjustment equalizeHist ");
        }

        // 合并通道并转换回RGB格式
        Core.merge(yuvChannels, yuvMt);
        Imgproc.cvtColor(yuvMt, outputImageMt, Imgproc.COLOR_YUV2RGB);
        Log.d(TAG,"adaptiveBrightnessAdjustment COLOR_YUV2RGB ");

        Utils.matToBitmap(outputImageMt,inBitmap);
        Log.d(TAG,"adaptiveBrightnessAdjustment matToBitmap ");

        rgbMt.release();
        yuvMt.release();
        outputImageMt.release();

        return inBitmap;
    }

    /**
     * @param inBitmap 输入图像
     * @param alpha 对比度系数。大于1的值会增加对比度，小于1的值会减少对比度
     * @param beta 亮度系数。正值增加亮度，负值减少亮度
     * @return 输出结果
     */
    private Bitmap adaptiveContrastAdjustment(Bitmap inBitmap,double alpha, int beta ){
        Log.d(TAG,"adaptiveContrastAdjustment in");
        Mat rgbMt = new Mat(inBitmap.getWidth(),inBitmap.getHeight(), CvType.CV_8UC(3));
        Mat outputImageMt = new Mat();
        Utils.bitmapToMat(inBitmap,rgbMt);
        Log.d(TAG,"adaptiveContrastAdjustment bitmapToMat");

        rgbMt.convertTo(outputImageMt,-1,alpha,beta);

        Utils.matToBitmap(outputImageMt,inBitmap);
        Log.d(TAG,"adaptiveContrastAdjustment matToBitmap ");

        rgbMt.release();
        outputImageMt.release();

        return inBitmap;
    }
}