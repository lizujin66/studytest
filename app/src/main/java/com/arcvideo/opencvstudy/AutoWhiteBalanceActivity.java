package com.arcvideo.opencvstudy;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class AutoWhiteBalanceActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "MainActivity";
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mGray;

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cameraview);

        requestPermissions();

        mOpenCvCameraView = findViewById(R.id.java_camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initLocal()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
        }else{
            mOpenCvCameraView.enableView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        // 自动亮度调节
        Mat adjustedImage = new Mat();
        adaptiveBrightnessAdjustment(mRgba, adjustedImage);

        return adjustedImage;
    }

    private void adaptiveBrightnessAdjustment(Mat inputImage, Mat outputImage) {
        // 转换为灰度图像
        Imgproc.cvtColor(inputImage, mGray, Imgproc.COLOR_RGBA2GRAY);

        // 计算图像的平均亮度
        double meanBrightness = Core.mean(mGray).val[0];

        // 根据平均亮度调整图像
        if (meanBrightness < 50) { // 夜晚环境
            // 使用自适应直方图均衡化
            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(mGray, mGray);
        } else { // 白天环境
            // 使用全局直方图均衡化
            Imgproc.equalizeHist(mGray, mGray);
        }

        // 将灰度图像转换回彩色图像
        Imgproc.cvtColor(mGray, outputImage, Imgproc.COLOR_GRAY2RGBA);
    }


private void requestPermissions() {
    if (allPermissionsGranted()) {
        return;
    }
    ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
}

private boolean allPermissionsGranted() {
    for (String permission : REQUIRED_PERMISSIONS) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
    }
    return true;
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
        if (allPermissionsGranted()) {
            // 权限已授予
        } else {
            // 权限被拒绝
        }
    }
}
}