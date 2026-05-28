package com.arcvideo.opencvstudy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.core.app.ActivityCompat;


@SuppressLint("NewApi")
public class ArcCamera2 {
	private static final String TAG = "ArcCamera2";

	private static final int FLASH_OFF = 1;
	private static final int FLASH_ON = 2;
	private static final int FLASH_AUTO = 4;
	private static final int FLASH_TORCH = 8;
	private static final int FLASH_RED_EYE = 16;

	private static volatile ArcCamera2 mArcCamera = null;                // ArcCamera静态对象
	private Context mContext = null;                            //设备上下文
	// 照相机控件
	private CameraManager mCameraMgr = null;
	private CameraDevice mCameraDevice = null;
	private CameraCharacteristics mCamCharactrts = null;
	private CameraCaptureSession mCamCaptSession = null;
	private CaptureRequest.Builder mCapReqBuilder = null;
	private Integer mSensorOrientation;
	private Surface mImgReaderSurface = null;
	private ImageReader mImageReader = null;
	/**
	 * A {@link Handler} for running tasks in the background.
	 */
	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;

	/**
	 * A {@link Semaphore} to prevent the app from exiting before closing the camera.
	 */
	private Semaphore mCameraSemaphoreLock = new Semaphore(1);

	private boolean mIfToGetCameraData = true;
	private ImgDataContainer mImgDataContainer = null;
	private Object mImgContaLock = new Object();
	private boolean mbThreadNeedExit = false;
	private List<ImageInfo> mList = null;
	private ProcessThread mProcThread = null;
	private SurfaceHolder mPreviewSurHolder = null;
	private boolean mIsCamaeraOpened = false;
	private boolean mIsSwitchingCamrea = false;
	private int mImgDataOutFormat = ArcTypes.ARC_PIXELFORMAT_UNKNOW;
	private int mImgDataInFormat = ArcTypes.ARC_PIXELFORMAT_UNKNOW;

	// output surfacetexture
	private SurfaceTexture mCameraOutputTexture = null;  // receives the output from the camera preview

	// 照相机配置
	private int mCameraDataRotate = 0;
	private int mFrameRate = 30;                                    // 预览帧率
	private int mVideoWidth = 0;                                    // 预览分辨率：宽
	private int mVideoHeight = 0;                                    // 预览分辨率：高
	private boolean mIsPreviewing = false;                            // 是否正在预览
	private int mAppOrientation = 0;                                    // 旋转角度
	private int[] mSupportedPreviewFps;                        // 支持的预览界面的fps
	private int mCurrentFps = 0;                                    // 摄像头出来的实际帧率
	private int mCameraFaceType = CameraCharacteristics.LENS_FACING_FRONT;    // 前后摄像头
	private boolean mbContinuousFoucs = true;                        // 是否连续对焦模式
	private static final int HANDLER_MSG_ONFRAMEAVAILABLE = 0x1001;    // 消息
	private int mExposureLevel = 0;
	private int mMaxExposureValue = 0;
	private int mMinExposureValue = 0;
	private int mExposureRange = 0;
	private ArcVFrame mArcVFrame = null;

	private ArcFrameDataCallBack mCameraFrameCallBack = null; //数据对外输出回调
	private Object mcbLockObject = new Object();

	ArrayList<Long> mTimeStamps = new ArrayList<Long>();

	private static boolean bOpenLogOutput = true;

	private static void printLog(int level, String logString) {
		if (!bOpenLogOutput) {
			return;
		}

		switch (level) {
			case 1:
				Log.e(TAG, logString);
				break;
			case 0:
			default:
				Log.i(TAG, logString);
				break;
		}
	}

	private void dumpToFile(byte[] data, int len) {
		String filePathString = "/sdcard/camera.nv21";
		File dFile = new File(filePathString);
		printLog(0, "dumpToFile filePathString = " + filePathString);
		if (!dFile.exists()) {
			try {
				dFile.createNewFile();
				FileOutputStream os = new FileOutputStream(dFile);
				if (len > 0) {
					printLog(0, "dumpToFile len = " + len);
					os.write(data, 0, len);
				}
				os.flush();
				os.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static class ImageInfo {
		final ImgBuffer dataBuffer;//byte[] data;
		final long timeStamp;
		final int pixelStride;
		final int rowPadding;
		final int width;
		final int height;

		ImageInfo(ImgBuffer data, long timestamp, int pixelStride, int rowPadding, int picWidth, int picHeight) {
			this.dataBuffer = data;
			this.timeStamp = timestamp;
			this.pixelStride = pixelStride;
			this.rowPadding = rowPadding;
			this.width = picWidth;
			this.height = picHeight;
		}
	}

	private class ImgBuffer {
		int syncIndex;
		byte[] mData;

		public ImgBuffer(int sync, byte[] data) {
			// TODO Auto-generated constructor stub
			syncIndex = sync;
			mData = data;
		}

		public int getSyncIndex() {
			return syncIndex;
		}
	}

	private class ImgDataContainer {
		int syncIndex;
		List<ImgBuffer> mReusableBuffers;

		public ImgDataContainer(int sync, List<ImgBuffer> bufferList) {
			syncIndex = sync;
			mReusableBuffers = bufferList;
		}

		public int getSyncIndex() {
			return syncIndex;
		}

		public List<ImgBuffer> getResuableBuffers() {
			return mReusableBuffers;
		}

		public void clear() {
			mReusableBuffers.clear();
			syncIndex++;
		}
	}

	/**
	 * new ArcCamera2
	 */
	private ArcCamera2(Context context) {
		mContext = context;
	}

	/**
	 * getInstance ArcCamera2
	 */
	public static ArcCamera2 getInstance(Context context) {
		//如下的写法是为了保证单例模式的多线程访问安全
		if (mArcCamera == null) {
			//同步代码块（对象未初始化时，使用同步代码块，保证多线程访问时对象在第一次创建后，不再重复被创建）
			synchronized (ArcCamera2.class) {
				//未初始化，则初始instance变量
				if (mArcCamera == null) {
					mArcCamera = new ArcCamera2(context);
				}
			}
		}
		return mArcCamera;
	}

	/**
	 * set camera facing type
	 * @param iFacingType
	 */
	public void setCameraFacingType(int iFacingType) {
		switch (iFacingType) {
			case ArcTypes.CAMERA_FACING_BACK:
				mCameraFaceType = CameraCharacteristics.LENS_FACING_BACK;
				break;
			case ArcTypes.CAMERA_FACING_FRONT:
			default:
				mCameraFaceType = CameraCharacteristics.LENS_FACING_FRONT;
				break;
		}
	}

	/**
	 * set surfaceHolder for preview
	 * @param sHolder
	 */
	public void setPreviewSurfaceHolder(SurfaceHolder sHolder) {
		printLog(0, "setPreviewSurfaceHolder in");
		if (!mIsPreviewing) {
			mPreviewSurHolder = sHolder;
		}
	}

	/**
	 * set surfaceTexture for camera data output
	 * use for opengl rendering
	 * @param surfTexture
	 */
	public void setOutputSurfaceTexture(SurfaceTexture surfTexture) {
		printLog(0, "setOutputSurfaceTexture in");
		if (!mIsPreviewing) {
			mCameraOutputTexture = surfTexture;
		}
	}

	public void configToGetImageDataForPreview(boolean enable) {
		mIfToGetCameraData = enable;
	}

	/**
	 * get camera facing type
	 *
	 * @return
	 */
	public int getCameraFacingType() {
		switch (mCameraFaceType) {
			case CameraCharacteristics.LENS_FACING_BACK:
				return ArcTypes.CAMERA_FACING_BACK;
			case CameraCharacteristics.LENS_FACING_FRONT:
				return ArcTypes.CAMERA_FACING_FRONT;
			default:
				return ArcTypes.CAMERA_FACING_NONE;
		}
	}

	/**
	 * Opens a camera, and attempts to establish preview mode at the specified width and height.
	 * <p>
	 * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
	 */
	public int openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
		if (mCameraDevice != null) {
			return 0;
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			throw new SecurityException("Unsupport current SDK API LEVEL, make sure API LEVEL >= 21");
		}

		mVideoWidth = desiredWidth;
		mVideoHeight = desiredHeight;
		mFrameRate = desiredFps;
		printLog(0, "openCamera mVideoWidth=" + mVideoWidth + "; mVideoHeight=" + mVideoHeight + "; mFrameRate=" + mFrameRate);

		if (null == mBackgroundThread) {
			startBackgroundThread();
		}

		if (null == mCameraMgr) {
			mCameraMgr = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
		}

		try {

			boolean facingChanged = false;
			CameraCharacteristics selectCharacteristics = null;
			String cameraId = null;
			for (String camId : mCameraMgr.getCameraIdList()) {
				selectCharacteristics = mCameraMgr.getCameraCharacteristics(camId);
				if (mCameraFaceType == selectCharacteristics.get(CameraCharacteristics.LENS_FACING)) {
					cameraId = camId;
					break;
				}
			}

			if (cameraId == null) {
				facingChanged = true;
				if (mCameraFaceType == CameraCharacteristics.LENS_FACING_FRONT) {
					mCameraFaceType = CameraCharacteristics.LENS_FACING_BACK;
				} else {
					mCameraFaceType = CameraCharacteristics.LENS_FACING_FRONT;
				}

				cameraId = mCameraMgr.getCameraIdList()[0];
				printLog(0, "openCamera camera facing changed mCameraFaceType = " + mCameraFaceType);
			}

			mCamCharactrts = selectCharacteristics;
			mSensorOrientation = mCamCharactrts.get(CameraCharacteristics.SENSOR_ORIENTATION);
			printLog(0, "openCamera camrea id is " + cameraId + ", mSensorOrientation = " + mSensorOrientation);

			int hwLevel = mCamCharactrts.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
			if (hwLevel == 3) {
				Log.e(TAG, "HARDWARE LEVEL IS INFO_SUPPORTED_HARDWARE_LEVEL_3, hwLevel = " + hwLevel);
			} else if (hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
				Log.e(TAG, "HARDWARE LEVEL IS INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, hwLevel = " + hwLevel);
			} else if (hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) {
				Log.e(TAG, "HARDWARE LEVEL IS INFO_SUPPORTED_HARDWARE_LEVEL_FULL, hwLevel = " + hwLevel);
			} else if (hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
				Log.e(TAG, "HARDWARE LEVEL IS INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED, hwLevel = " + hwLevel);
			} else {
				Log.e(TAG, "HARDWARE LEVEL IS unknow, hwLevel = " + hwLevel);
			}

			Log.d(TAG, "openCamera tryAcquire");
			if (!mCameraSemaphoreLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			printLog(0, "try mCameraMgr.openCamera thread id = " + Thread.currentThread().getId() + ", thread name = " + Thread.currentThread().getName());
			if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				// TODO: Consider calling
				//    ActivityCompat#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for ActivityCompat#requestPermissions for more details.
				printLog(0, "mCameraMgr.openCamera no permission ");
				return -1;
			}
			mCameraMgr.openCamera(cameraId, mDeviceStateCallback, null);
            
            printLog(0, "mCameraMgr.openCamera end mCameraDevice = " + mCameraDevice);
            
            if (facingChanged) {
				return 1;
			}else{
				if (mVideoWidth != desiredWidth 
						|| mVideoHeight != desiredHeight) {
					Log.w(TAG, "openCamera resolution changed mVideoWidth = " + mVideoWidth + ", mVideoHeight = " + mVideoHeight);
				}
				return 0;
			}
            
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return -3;
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
            return -1;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
	}
	
	public boolean isOpened(){
		return mIsCamaeraOpened;
	}
   
    /**
     * 释放camera
     * @return
     */
    public void releaseCamera() {
    	printLog(0, "releaseCamera() IN");
    	try {
            mCameraSemaphoreLock.acquire();
            closeCaptureSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mCameraSemaphoreLock.release();
        }
    	printLog(0, "releaseCamera() mBackgroundThread");
    	if (null != mBackgroundThread) {
    		stopBackgroundThread();
		}
    	
    	printLog(0, "releaseCamera() mProcThread");
    	mbThreadNeedExit = true;
    	if (null != mProcThread) {
    		try {
            	mProcThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mProcThread = null;
		}
              
        printLog(0, "releaseCamera() mImageReader");
	    if (mImageReader != null) {
	        mImageReader.close();
	        mImageReader = null;
	    }
	    	
    	if (mImgReaderSurface != null) {
    		mImgReaderSurface.release();
    		mImgReaderSurface = null;
		}
        
        if (mList != null) {
        	mList.clear();
		}
        
        synchronized (mImgContaLock) {
			if (mImgDataContainer != null) {
				mImgDataContainer.clear();
			}
		}
        mCamCharactrts = null;
        mCameraMgr = null;
        mIsPreviewing = false;
        mIsCamaeraOpened = false;
        mIsSwitchingCamrea = false;
        mPreviewToCameraTransform = null;
        zoom_level = 1;
        mCameraOutputTexture = null;
        printLog(0, "releaseCamera -- done");
    }
	
    /**
     * open camera preview ,user surfaceTexture.
     */
   public void openPreview(int orientation) {
		if(mIsPreviewing || null == mCameraDevice)
			return;
		printLog(0, "openPreview orientation = " + orientation);		
		setCameraDisplayOrientation(orientation);
		
        printLog(0, "starting camera preview mCameraTexture = " + mCameraOutputTexture);
    
    	if(mCameraOutputTexture != null){
    		printLog(0, "openPreview set texture size mVideoWidth = " + mVideoWidth + ", mVideoHeight = " + mVideoHeight);
    		mCameraOutputTexture.setDefaultBufferSize(mVideoWidth, mVideoHeight);
    	}
    	
    	if (mIfToGetCameraData) {
			initImageReader();
		}
    	
    	try {
            closeCaptureSession();
            List<Surface> surfaceList = new ArrayList<Surface>(); 
            Surface camSurface = null;
            Surface previewSurface = null;
            if (null != mPreviewSurHolder) {
            	mPreviewSurHolder.setFixedSize(mVideoWidth, mVideoHeight);
            	previewSurface = mPreviewSurHolder.getSurface();
			}
            
            
            if (null != mCameraOutputTexture) {
				camSurface = new Surface(mCameraOutputTexture);
			}
            
            mCapReqBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            if (previewSurface != null) {
            	mCapReqBuilder.addTarget(previewSurface);
                surfaceList.add(previewSurface);
			}
            
            if (camSurface != null) {
            	mCapReqBuilder.addTarget(camSurface);
                surfaceList.add(camSurface);
			}

            if (null != mImgReaderSurface) {
            	mCapReqBuilder.addTarget(mImgReaderSurface);
                surfaceList.add(mImgReaderSurface);
			}
            printLog(0, "openPreview createCaptureSession ");
            mCameraDevice.createCaptureSession(surfaceList,mCapSessionStateCallback, mBackgroundHandler);
            
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    	
        mIsPreviewing = true;
        
        printLog(0, "starting camera preview SUCCESS");
	}

   /**
	 * Stops camera preview
	 */
   public void closePreview() {
		if (mCamCaptSession != null) {
            closeCaptureSession();
            mIsPreviewing = false;
            printLog(0, "stopPreview -- done");
		}
	}
	  
	/**
     * switch camera camera facing
     */
   public void switchCamera() {		
		switch (mCameraFaceType) {
		case CameraCharacteristics.LENS_FACING_BACK:
			mCameraFaceType = CameraCharacteristics.LENS_FACING_FRONT;
			break;
		case CameraCharacteristics.LENS_FACING_FRONT:
		default:
			mCameraFaceType = CameraCharacteristics.LENS_FACING_BACK;
			break;
		}
		
		printLog(0, "mCameraFaceType = " + mCameraFaceType);
		releaseCamera();
		
		mIsSwitchingCamrea = true;
		openCamera(mVideoWidth, mVideoHeight, mFrameRate);
	}
   
   /**
	 * 设置当前设备旋转角度，该旋转角度用来配置camera显示角度，
	 * 当屏幕旋转时必须调用该接口更新旋转角度
	 * 
	 * @param orientation
	 */
	public void setCameraDisplayOrientation(int orientation) {
		if (mCameraFaceType == CameraCharacteristics.LENS_FACING_FRONT) {
			mCameraDataRotate = (mSensorOrientation + orientation) % 360;
			mCameraDataRotate = (360 - mCameraDataRotate) % 360; // compensate the mirror
		} else {
			// back-facing
			mCameraDataRotate = (mSensorOrientation - orientation + 360) % 360;
		}
		mAppOrientation = orientation;
		printLog(0, "setCameraDisplayOrientation mAppOrientation = " + mAppOrientation + ", mCameraDataRotate = " + mCameraDataRotate);
	}

	/**
	 * get camera data display rotate
	 * @return
	 */
	public int getCameraRotate(){
		return mCameraDataRotate;
	}

	
   /**
    * 设置camera预览分辨率，如果不支持则自动选择与设定分辨率宽高比最接近的分辨率，
    * opencamera之后可通过 getCurrentPreviewSize查看当前实际使用的预览分辨率
    * 
    * @param desiredWidth
    * @param desiredHeight
    */
   public void setCameraPreviewSize(int desiredWidth, int desiredHeight) {
   	
	   mVideoHeight = desiredHeight;
	   mVideoWidth = desiredWidth;
	   
	   releaseCamera();
	   openCamera(mVideoWidth, mVideoHeight, mFrameRate);
   }

	
   /**
   * set camera torch state, open or close.
   */
   public boolean setTorchState(boolean enable) {
	   boolean setSuccess = false;
	   if (mCameraFaceType == CameraCharacteristics.LENS_FACING_FRONT) {
		   return setSuccess;
	   }
	   
       if (mCapReqBuilder != null) {
    	   if (enable) {
    		   updateFlashMode(FLASH_TORCH);
    	   }else{
    		   updateFlashMode(FLASH_OFF);
    	   }
		   
		   if (mCamCaptSession != null) {
	           try {
	        	   mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(),
	                       null, null);
	        	   setSuccess = true;
	           } catch (CameraAccessException e) {
	        	   e.printStackTrace();
	        	   setSuccess = false;
	           }
	       }
       }
    	  
		return setSuccess;
	}
	
	/**
    * set camera exposure state
    * 
    * @param exposure
    * @return
	*/
	public boolean setExposure(int exposure) {
		if (mCamCharactrts == null) {
			return false;
		}
		
		if (mMaxExposureValue == 0 && mMinExposureValue == 0) {		
			Range<Integer> range1 = mCamCharactrts.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
			mMaxExposureValue = range1.getUpper().intValue();
			mMinExposureValue = range1.getLower().intValue();
			if (mMaxExposureValue == 0 && mMinExposureValue == 0) {
				return false;
			}
			mExposureRange = (-mMinExposureValue) + mMaxExposureValue;
		}
		printLog(0, "setExposure mMinExposureValue = " + mMinExposureValue + ", mMaxExposureValue = " + mMaxExposureValue);
		
		boolean setSuccess = false;
		int time = 100 / mExposureRange;
		//mExposureLevel = (exposure * mExposureRange / 100) - (mExposureRange / 2);
		mExposureLevel = ((exposure / time) - mMaxExposureValue) > mMaxExposureValue ? mMaxExposureValue : ((exposure / time) - mMaxExposureValue) < mMinExposureValue ? mMinExposureValue : ((exposure / time) - mMaxExposureValue);	
		printLog(0, "setExposure mExposureLevel = " + mExposureLevel);
		mCapReqBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mExposureLevel);
		
		if (mCamCaptSession != null) {
           try {
        	   mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(),
                       null, null);
        	   setSuccess = true;
           } catch (CameraAccessException e) {
        	   e.printStackTrace();
        	   setSuccess = false;
           }
       }
		return setSuccess;
	}
	
	/**
	 * get current exposure compensation
	 * 
	 * @return
	 */
	public int getCurrentExposureLevel() {
		return mExposureLevel;
	}
	
	/**
	 * 设置获取Camera数据的回调，多次设置会自动覆盖前面的设置，最后一次设置有效
	 * @param callBack
	 */
	public void setCameraDataCallBack(ArcFrameDataCallBack callBack){
		synchronized (mcbLockObject) {
			mCameraFrameCallBack = callBack;
		}	
	}
   
   /**
    * enable auto Foucs . true: continuousFoucs， false: touch Foucs
    * @param isContinuousFoucs
    * @return
    */
   public boolean enableAutoFoucs(boolean isContinuousFoucs) {
	   mbContinuousFoucs = isContinuousFoucs;
	   return setAutoFoucs(mbContinuousFoucs);
   }
   
   /**
    * set camera foucs state, Continuous or not.
    * @param isContinuousFoucs
    * @return
    */
   private boolean setAutoFoucs(boolean isContinuousFoucs) {
	   boolean setSuccess = false;
       if (mCapReqBuilder != null) {
    	   if (isContinuousFoucs) {
    		   int[] modes = mCamCharactrts.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
	           // Auto focus is not supported
	           if (modes == null || modes.length == 0 ||
	                   (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
	               mCapReqBuilder.set(CaptureRequest.CONTROL_AF_MODE,
	                       CaptureRequest.CONTROL_AF_MODE_OFF);
	           } else {
	        	   mCapReqBuilder.set(CaptureRequest.CONTROL_AF_MODE,
	                       CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
	        	   setSuccess = true;
	           } 
    	   }else {
        	   mCapReqBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                       CaptureRequest.CONTROL_AF_MODE_OFF);
           }         
       } 
    	   
       if (mCamCaptSession != null) {
           try {
        	   mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(),
                       null, null);
        	   setSuccess = true;
           } catch (CameraAccessException e) {
        	   e.printStackTrace();
        	   setSuccess = false;
           }
       }
	   return setSuccess;
   }
   
   /**
    * set foucs rect when Foucs mode is FOCUS_MODE_AUTO.
    * @param pointF touch point
    * @param surfaceWidth app surfaceView width
    * @param surfaceHeight app surfaceView width
    */
	public boolean setFocusAreas(PointF pointF, int surfaceWidth, int surfaceHeight) {
		printLog(0, "setFocusAreas in");
		if (mCapReqBuilder == null) 
			return false;
		
		if (mCamCharactrts.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) < 1) {
			printLog(0, "setFocusAreas not supprot");
			return false;
		}

        if (null == mPreviewToCameraTransform) {
			coordinateTransformer(mCamCharactrts, mVideoWidth,mVideoHeight);
		}
		
        Rect touchRect = new Rect(0, 0, Math.round(pointF.x), Math.round(pointF.y));
        MeteringRectangle focusAreaTouch = toCameraSpace(touchRect);
		//first stop the existing repeating request
        try {
			mCamCaptSession.stopRepeating();
			//cancel any existing AF trigger (repeated touches, etc.)
	        mCapReqBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
	        mCapReqBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
	        mCamCaptSession.capture(mCapReqBuilder.build(), mCapSessionCallback, mBackgroundHandler);

	        //Now add a new AF trigger with focus region
	        mCapReqBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
	        mCapReqBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
	        mCapReqBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
	        mCapReqBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
	        mCapReqBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

	        //then we ask for a single request (not repeating!)
	        mCamCaptSession.capture(mCapReqBuilder.build(), mCapSessionCallback, mBackgroundHandler);
	        printLog(0, "setFocusAreas mCamCaptSession.capture");
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}	
	
	private int zoom_level = 0;
	/**
	 * set camera Zoom 
	 * @param isZoomOut true means zoom++, false means zoom--
	 * @return
	 */
	public boolean setZoom(boolean isZoomOut) {
		printLog(0, "setZoom isZoomOut = " + isZoomOut);
		if (mCamCharactrts == null) {
			return false;
		}
		
		float maxZoom = (mCamCharactrts.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
		float maxZoom_Enlarge = maxZoom * 10;//乘以10目的是为了让放大的粒度更细腻，放大过程看起来更平滑
        Rect m = mCamCharactrts.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        printLog(0, "setZoom maxZoom = " + maxZoom + ", maxZoom_Enlarge = " + maxZoom_Enlarge);
        if(isZoomOut && maxZoom_Enlarge > zoom_level){
            zoom_level++;

        }
        else if (!isZoomOut && zoom_level > 0){
            zoom_level--;
        }else{
        	return false;
        }
        
        if (zoom_level != 0 && zoom_level % 2 == 0) {
			return true;
		}
        
        int maxZoomInteger = (int)maxZoom_Enlarge;
        printLog(0, "setZoom zoom_level = " + zoom_level + ", maxZoomInteger = " + maxZoomInteger);
        int minW = (int) (m.width() / maxZoomInteger);
        int minH = (int) (m.height() / maxZoomInteger);
        int difW = m.width() - minW;
        int difH = m.height() - minH;
        int cropW = difW /maxZoomInteger *(int)zoom_level;
        int cropH = difH /maxZoomInteger *(int)zoom_level;
        cropW -= cropW & 3;
        cropH -= cropH & 3;
        printLog(0, "setZoom cropW = " + cropW + ", cropH = " + cropH + ", width = " + m.width() + ", height = " + m.height());
        float zoomTime_width = (float)m.width()/(float)(m.width() - cropW);
        float zoomTime_height = (float)m.height()/(float)(m.height() - cropH);
        float zoomTime = zoomTime_width > zoomTime_height ? zoomTime_width : zoomTime_height;
        printLog(0, "setZoom zoomTime_width = " + zoomTime_width + ", zoomTime_height = " + zoomTime_height + ", zoomTime = " + zoomTime);
        if (zoomTime > maxZoom) {
			return true;
		}
        
        Rect zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
        try {
        	//mCamCaptSession.stopRepeating();
        	mCapReqBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(), null,null);
            //mCamCaptSession.capture(mCapReqBuilder.build(), null, null);
            printLog(0, "setZoom  mCamCaptSession.capture");
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (NullPointerException ex)
        {
            ex.printStackTrace();
        }

		return false;
	}
	
	 /**
	 * set camera whitebalance state
	 * 
	 * @param value
	 */
	private boolean setWhiteBalance(String value) {
		
		return false;
	}
		
	/**
	 * get default white balance
	 * @return
	 */
	private String getDefaultWhiteBalance() {
		return "auto"; // chosen to match Camera.Parameters.WHITE_BALANCE_AUTO,
						// but we also use compatible values for Camera2 API
	}
	
	/**
	 * get min exposure compensation
	 * 
	 * @return
	 */
	private int getMinExposureCompensation() {
		return 0;
	}
	
	/**
	 * get max exposure compensation
	 * 
	 * @return
	 */
	private int getMaxExposureCompensation() {
		return 0;
	}
	
   
	/**
	 * set hdr state
	 * @param enabled
	 * @return
	 */
	private boolean setHDRState(boolean enabled) {
		if (!isSupportHDR()) {
			return false;
		}

		return true;
	}
	
	/**
	 * is support hdr
	 * @return
	 */
	private boolean isSupportHDR() {
		return false;
	}

	
	/**
	 * get camera support preview fps
	 * 
	 * @return int[]
	 */
	private int[] getSupportPreviewFps() {
		if(mSupportedPreviewFps == null) {
			;
		}
		return mSupportedPreviewFps;
	}

	
	/**
	 * When preview data is ready, the callback will draw frame onto the
	 * SurfaceView.
	 * 这里需要注意，使用OpenGL进行texture的操作时不建议直接在callback函数中进行updateTexImage()操作，原因请看如下官方说明：
	 * SurfaceTexture objects may be created on any thread. 
	 * updateTexImage() may only be called on the thread with the OpenGL ES context that contains the texture object. 
	 * The frame-available callback is called on an arbitrary thread, 
	 * so unless special care is taken updateTexImage() should not be called directly from the callback
	 */
	private SurfaceTexture.OnFrameAvailableListener mFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {

		@Override
		public void onFrameAvailable(SurfaceTexture surfaceTexture) {
			if (mDrawFrameHandler != null) {
				mDrawFrameHandler.sendEmptyMessage(HANDLER_MSG_ONFRAMEAVAILABLE);
			}
		}
	};

	//(Looper.myLooper() == null ? Looper.getMainLooper(): Looper.myLooper())
    protected Handler mDrawFrameHandler = new Handler(Looper.getMainLooper()) {
    	public void handleMessage(Message msg) {
    		switch (msg.what) {
			case HANDLER_MSG_ONFRAMEAVAILABLE:
				mDrawFrameHandler.removeMessages(HANDLER_MSG_ONFRAMEAVAILABLE);
				break;
			default:
				break;
			}
    	}
	};
	
	private boolean isScreenLandScape() {
		Configuration mConfiguration = mContext.getResources().getConfiguration(); // 获取设置的配置信息
		int ori = mConfiguration.orientation; // 获取屏幕方向

		if (ori == mConfiguration.ORIENTATION_LANDSCAPE) {
			// 横屏
			return true;
		} else if (ori == mConfiguration.ORIENTATION_PORTRAIT) {
			// 竖屏
			return false;
		}
		return false;
	}
	
	/**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
        	printLog(0, "mDeviceStateCallback: camera onOpened thread id = " + Thread.currentThread().getId() + ", thread name = " + Thread.currentThread().getName());
    		mCameraDevice = cameraDevice;          
            mCameraSemaphoreLock.release();
            mIsCamaeraOpened = true;
            if (mIsSwitchingCamrea) {
            	openPreview(mAppOrientation);
            	mIsSwitchingCamrea = false;
			}
            //if (null != mTextureView) {
            //    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            //}
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
        	printLog(0, "mDeviceStateCallback: camera onDisconnected");
            mCameraSemaphoreLock.release();
            cameraDevice.close();
        	mCameraDevice = null;    
        	mIsCamaeraOpened = false;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
        	printLog(0, "mDeviceStateCallback: camera onError error = " + error);
            mCameraSemaphoreLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mIsCamaeraOpened = false;
        }
    };
    
    private CameraCaptureSession.CaptureCallback mCapSessionCallback = new CameraCaptureSession.CaptureCallback() {
    	@Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (request.getTag() == "FOCUS_TAG") {
                //the focus trigger is complete -
                //resume repeating (preview surface will get frames), clear AF trigger
                mCapReqBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,null);
                printLog(0, "onCaptureCompleted");
                try {
					mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(), null, mBackgroundHandler);
				} catch (CameraAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (request.getTag() == "FOCUS_TAG") {
            	Log.e(TAG, "Manual AF failure: " + failure);
            }           
        }
	};
	
    private CameraCaptureSession.StateCallback mCapSessionStateCallback = new CameraCaptureSession.StateCallback() {
		
		@Override
		public void onConfigured(CameraCaptureSession session) {
			// TODO Auto-generated method stub
			printLog(0,"mCapSessionStateCallback onConfigured");
			mCamCaptSession = session;
            updatePreview();
		}
		
		@Override
		public void onConfigureFailed(CameraCaptureSession arg0) {
			// TODO Auto-generated method stub
			printLog(0,"mCapSessionStateCallback onConfigureFailed");
			mCamCaptSession = null;
		}
	};
    
	/**
     * Update the camera preview. {@link #updatePreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
        	printLog(0,"updatePreview()");
            setUpCaptureRequestBuilder(mCapReqBuilder);
            HandlerThread thread = new HandlerThread("Camera2");
            thread.start();
            mCamCaptSession.setRepeatingRequest(mCapReqBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
    }
    
    private void closeCaptureSession(){
    	if (null != mCamCaptSession) {
			mCamCaptSession.close();
			mCamCaptSession = null;
		}
    }
    
    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void initImageReader(){
    	if (mImageReader == null) {
        	mImageReader = ImageReader.newInstance(mVideoWidth, mVideoHeight, ImageFormat.YUV_420_888, 3);
		}
        
        if (mImgReaderSurface == null && mImageReader != null) {
        	mImgReaderSurface = mImageReader.getSurface();
		}
        
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
        
        if (mImgDataContainer == null) {
			mImgDataContainer = new ImgDataContainer(0, Collections.synchronizedList(new ArrayList<ImgBuffer>()));
		}else{
			synchronized (mImgContaLock) {
				mImgDataContainer.clear();
			}	
		}
        
        if (mList == null) {
        	mList = Collections.synchronizedList(new ArrayList<ImageInfo>());
		}   
        
        if (mProcThread == null) {
        	mProcThread = new ProcessThread();
            mProcThread.start();
            mbThreadNeedExit = false;
		}
    }
    
    long totalTime0 = 0;
   	long totalCount0 = 0;
   	long firstTime0 = 0;
	private ImageReader.OnImageAvailableListener mOnImageAvailableListener  = new ImageReader.OnImageAvailableListener() {
	   	@Override
        public void onImageAvailable(ImageReader reader) {
   		 	printLog(0,"in onImageAvailable");
            Image image = null;
            try {
            	image = reader.acquireNextImage();//获取下一个
			} catch (RuntimeException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
            
            if (image == null) {
				return;
			}
            
            if (ArcTypes.ARC_PIXELFORMAT_UNKNOW == mImgDataInFormat) {
       	    	mImgDataInFormat = checkYUVImageDataColorFormat(image);
       	    	if (ArcTypes.ARC_PIXELFORMAT_UNKNOW == mImgDataOutFormat) {
       	    		//如果没有指定mImgDataOutFormat，则默认将其设置为mImgDataInFormat一样
    				mImgDataOutFormat = mImgDataInFormat;
    			}
    		} 	    
            
            if (firstTime0 == 0) {
				firstTime0 = System.currentTimeMillis();
			}
            totalCount0++;
            printLog(0,"OnImageAvailableListener img count = " + mList.size() + ", totalCount0 = " + totalCount0);
            if (mList.size() < 10) {
            	long time1 = System.currentTimeMillis();
            	Image.Plane[] planes = image.getPlanes();
	            int width = image.getWidth();//设置的宽
	            int height = image.getHeight();//设置的高
	            int pixelStride = planes[0].getPixelStride();//内存对齐参数,一个像素占几个字节大小
	            int rowStride = planes[0].getRowStride();//一行width个像素占多少字节大小
	            int rowPadding = rowStride - pixelStride * width; //每一行因对齐处理跨越的字节数
	            long timeStamp = image.getTimestamp();
	            
	            printLog(0,"OnImageAvailableListener getDataFromImage begin");
	            byte[] data = getDataFromImage(image,mImgDataOutFormat);
	            printLog(0,"OnImageAvailableListener getDataFromImage end");
	           
		        //dumpToFile(data, data.length);
		        printLog(0,"onImageAvailable getdata cost time = " + (System.currentTimeMillis() - time1) + ", thread-id = " + Thread.currentThread().getId());
		        mList.add(new ImageInfo(new ImgBuffer(mImgDataContainer.getSyncIndex(), data)
		        	, timeStamp, pixelStride, rowPadding, mVideoWidth,mVideoHeight));         
            }
            image.close();//用完需要关闭
            
            if (totalCount0 > 100) {
            	String showString = "avg cost time = " + (System.currentTimeMillis() - firstTime0)/totalCount0;
            	printLog(0,"onImageAvailable perimage cost time = " + (System.currentTimeMillis() - firstTime0)/totalCount0);         	
				totalCount0 = 0;
				firstTime0 = System.currentTimeMillis();
			}
        }
	};
	
   	private byte[] getBuffer(int width, int height, int format) {
        if (mImgDataContainer != null 
        		&& !mImgDataContainer.getResuableBuffers().isEmpty()) {
        	return mImgDataContainer.getResuableBuffers().remove(0).mData;
        } else {
        	return new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];        
        }
    }
   	
   	long totalTime = 0;
   	long totalCount = 0;
   	long firstTime = 0;
   	private class ProcessThread extends Thread {
        @Override
        public void run() {
            while (!mbThreadNeedExit) {
                if (mList.isEmpty()) {
                    SystemClock.sleep(1);
                    continue;
                }
                
                long time1 = System.currentTimeMillis();
                long time2 = 0;
                long time3 = 0;
                if (firstTime == 0) {
					firstTime = time1;
				}
                
                ImgBuffer buffer = null;
                final ImageInfo info = mList.remove(0);
                buffer = info.dataBuffer;
                final int pixelStride = info.pixelStride;
                final int rowPadding = info.rowPadding;
                final long timeStamp = info.timeStamp;
                int stride = info.width + rowPadding/pixelStride;
                int height = info.height;
                int width = info.width;

                
                byte[] continusBuffer = null;
                printLog(0,"ProcessThread process imgdata on thread-id = " + Thread.currentThread().getId());        
                if (continusBuffer == null) {
					continusBuffer = buffer.mData;
					width = width;
					printLog(0,"ProcessThread continusBuffer equ with data ");
				}             

                //dumpToFile(continusBuffer,continusBuffer.length);
                time2 = System.currentTimeMillis();
                if (mArcVFrame == null) {
					mArcVFrame = new ArcVFrame(null, -1, 0, 0, 0, mImgDataOutFormat, 0, 0);
				}
				
                mArcVFrame.mTimeStmp = timeStamp;
				mArcVFrame.mFrameBuffer = continusBuffer;//data
				mArcVFrame.mFrameWidth = width;
				mArcVFrame.mFrameHeight = height;
				mArcVFrame.mFrameStride = stride;
				mArcVFrame.mRotation = mCameraDataRotate;	
				
				printLog(0,"ProcessThread mWidth = " + mVideoWidth + ", mHeight = " + mVideoHeight );
				//to deal with data
				synchronized (mcbLockObject) {
					if (mCameraFrameCallBack != null) {
						mCameraFrameCallBack.onFrame(mArcVFrame);
					}
				}
				
				totalCount++;
				time3 = System.currentTimeMillis();
				printLog(0,"ProcessThread copyTime = " + (time2 - time1) + ", sendTime = " + (time3-time2));
				if (totalCount > 100) {
					printLog(0,"ProcessThread processtime_avg = " + (time3 - firstTime)/totalCount);
					totalCount = 0;
					firstTime = System.currentTimeMillis();
				}
				
				synchronized (mImgContaLock) {
					if (buffer != null 
							&& buffer.getSyncIndex() == mImgDataContainer.getSyncIndex()) {
						mImgDataContainer.getResuableBuffers().add(buffer);
					}else{
						Log.e(TAG, "drop current buffer syncIndex = " + buffer.getSyncIndex());
					}
				}					
            }
        }
	}
   	
   	private static boolean isImageFormatSupported(Image image) {
   	    int format = image.getFormat();
   	    switch (format) {
   	        case ImageFormat.YUV_420_888:
   	        case ImageFormat.NV21:
   	        case ImageFormat.YV12:
   	            return true;
   	    }
   	    return false;
   	}
   	
   	/**
   	 * 通过Image中每个plane数据及参数的特点来判断Image中
   	 * 的数据实际是YUV各种中的哪一种(I420，NV21....)
   	 * 该方法为作者自己总结，并没有什么官方标准做法，所以自己总结，不保证百分百正确
   	 * @param image
   	 * @return
   	 */
   	private int checkYUVImageDataColorFormat(Image image){
   		if (null == image) {
			return ArcTypes.ARC_PIXELFORMAT_UNKNOW;
		}
   		
   		int colorFormat = ArcTypes.ARC_PIXELFORMAT_UNKNOW;
   		Image.Plane[] planes = image.getPlanes();
   		if (planes.length < 3) {
			return ArcTypes.ARC_PIXELFORMAT_UNKNOW;
		}
   		
   		int rowStride_Y = planes[0].getRowStride();
   		int pixelStride_Y = planes[0].getPixelStride();
   		int dataSize_Y = planes[0].getBuffer().remaining();
   		int rowStride_U = planes[1].getRowStride();
   		int pixelStride_U = planes[1].getPixelStride();
   		int dataSize_U = planes[1].getBuffer().remaining();
   		int rowStride_V = planes[2].getRowStride();
   		int pixelStride_V = planes[2].getPixelStride();
   		int dataSize_V = planes[2].getBuffer().remaining();
   		
   		printLog(0, "checkYUVImageDataColorFormat rowStride_Y = " + rowStride_Y + ", pixelStride_Y = " + pixelStride_Y + ", dataSize_Y = " + dataSize_Y);
   		printLog(0, "checkYUVImageDataColorFormat rowStride_U = " + rowStride_U + ", pixelStride_U = " + pixelStride_U + ", dataSize_U = " + dataSize_U);
   		printLog(0, "checkYUVImageDataColorFormat rowStride_V = " + rowStride_V + ", pixelStride_V = " + pixelStride_V + ", dataSize_V = " + dataSize_V);
   		
   		if (rowStride_Y == rowStride_U && rowStride_Y == rowStride_V
   				&& pixelStride_U == 2 && pixelStride_V == 2) {
			colorFormat = ArcTypes.ARC_PIXELFORMAT_NV21;
		}else if (rowStride_Y == rowStride_U*2 && rowStride_Y == rowStride_V*2
   				&& pixelStride_U == 1 && pixelStride_V == 1
   				&& dataSize_Y == dataSize_U*4 && dataSize_Y == dataSize_V*4) {
			colorFormat = ArcTypes.ARC_PIXELFORMAT_I420;
		}else{
			colorFormat = ArcTypes.ARC_PIXELFORMAT_UNKNOW;
		}
   		
   		printLog(0, "checkYUVImageDataColorFormat colorFormat = " + colorFormat);
   		
   		return colorFormat;		
   	}

   	private byte[] getDataFromImage(Image image, int outColorFormat) {
   	    if (outColorFormat != ArcTypes.ARC_PIXELFORMAT_I420 && outColorFormat != ArcTypes.ARC_PIXELFORMAT_NV21) {
   	        throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
   	    }
   	    if (!isImageFormatSupported(image)) {
   	        throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
   	    }
   	    
   	    printLog(0, "getDataFromImage mImgDataOutFormat = " + mImgDataOutFormat);
   	    
   	    Rect crop = image.getCropRect();
   	    int format = image.getFormat();
   	    int width = crop.width();
   	    int height = crop.height();
   	    int imgWidth = image.getWidth();
   	    Image.Plane[] planes = image.getPlanes();
   	    byte[] data = getBuffer(width, height, format);
		int planeRowStride = planes[0].getRowStride();
   	    
   	    printLog(0,"get data from " + planes.length + " planes" + ", corp.width = " + width + ", imgWidth = " + imgWidth);
   	    if (outColorFormat != mImgDataInFormat
  	    		|| width != imgWidth
				|| width != planeRowStride
   	    		|| ArcTypes.ARC_PIXELFORMAT_UNKNOW == mImgDataInFormat) {
   	    	getYUV420_888Data(data,image,outColorFormat);
		}else{
			if (ArcTypes.ARC_PIXELFORMAT_NV21 == mImgDataInFormat) {
				getYUV420_888DataAsNV21(data, image);
			}else if (ArcTypes.ARC_PIXELFORMAT_I420 == mImgDataInFormat) {
				getYUV420_888DataAsI420(data, image);
			}
		}
   	    
   	    return data;
   	}
   	
   	private void getYUV420_888Data(byte[] datas, Image image, int outColorFormat){
   		Rect crop = image.getCropRect();
   	    int width = crop.width();
   	    int height = crop.height();
   	    Image.Plane[] planes = image.getPlanes();
   	    byte[] rowData = new byte[planes[0].getRowStride()];
   		int outputpixelOffset = 0;
   	    int outputPixelStride = 1;
   	    for (int i = 0; i < planes.length; i++) {
   	        switch (i) {
   	            case 0:
   	                outputpixelOffset = 0;
   	                outputPixelStride = 1;
   	                break;
   	            case 1:
   	                if (outColorFormat == ArcTypes.ARC_PIXELFORMAT_I420) {
   	                    outputpixelOffset = width * height;
   	                    outputPixelStride = 1;
   	                } else if (outColorFormat == ArcTypes.ARC_PIXELFORMAT_NV21) {
   	                    outputpixelOffset = width * height + 1;
   	                    outputPixelStride = 2;
   	                }
   	                break;
   	            case 2:
   	                if (outColorFormat == ArcTypes.ARC_PIXELFORMAT_I420) {
   	                    outputpixelOffset = (int) (width * height * 1.25);
   	                    outputPixelStride = 1;
   	                } else if (outColorFormat == ArcTypes.ARC_PIXELFORMAT_NV21) {
   	                    outputpixelOffset = width * height;
   	                    outputPixelStride = 2;
   	                }
   	                break;
   	        }
   	        ByteBuffer buffer = planes[i].getBuffer();
   	        int rowStride = planes[i].getRowStride();
   	        int pixelStride = planes[i].getPixelStride();
   	        printLog(0, "getDataFromImage pixelStride = " + pixelStride + ",rowStride = " + rowStride + ", width = " + width 
   	        		+ ", height = " + height + "buffer size = " + buffer.remaining());
   	        int shift = (i == 0) ? 0 : 1;
   	        int w = width >> shift;
   	        int h = height >> shift;
   	        printLog(0, "getDataFromImage crop.top = " + crop.top + ",crop.left = " + crop.left + ",crop.bottom = " + crop.bottom + ",crop.right = " + crop.right);
   	        buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
   	        if (pixelStride == 1 && outputPixelStride == 1) {
   	        	if (rowStride == width) {
   	        		buffer.get(datas, outputpixelOffset, buffer.remaining());
				}else{
					for (int row = 0; row < h; row++) {
						buffer.get(datas, outputpixelOffset, width);
						outputpixelOffset += width;
						if (row < h - 1) {
		   	                buffer.position(buffer.position() + rowStride - width);
		   	            }
					}
				}  	        	
			}else{			
				for (int row = 0; row < h; row++) {
	   	            int length;
   	                length = (w - 1) * pixelStride + 1;
   	                buffer.get(rowData, 0, length);
   	                for (int col = 0; col < w; col++) {
   	                	datas[outputpixelOffset] = rowData[col * pixelStride];
   	                    outputpixelOffset += outputPixelStride;
   	                }
	   	            if (row < h - 1) {
	   	                buffer.position(buffer.position() + rowStride - length);
	   	            }
				}
			}
   	        printLog(0, "Finished reading data from plane " + i);
   	    }
   	}
   	
   	/**
   	 * 这里处理的是Y,U,V各个plane都是连续内存存放的情况，如果为非连续内存存放则可使用
   	 * getYUV420_888Data函数
   	 * 
   	 * @param datas
   	 * @param image
   	 */
   	private void getYUV420_888DataAsI420(byte[] datas, Image image){
   		printLog(0, "getYUV420_888DataAsI420");
   		Rect crop = image.getCropRect();
   	    int width = crop.width();
   	    int height = crop.height();
   	    Image.Plane[] planes = image.getPlanes();
   	    int outputpixelOffset = 0;
   	    //Y plane
   	    ByteBuffer buffer = planes[0].getBuffer();
        int bufLenght = buffer.remaining();
        buffer.get(datas, outputpixelOffset, bufLenght);
        outputpixelOffset += bufLenght;
        //U plane
        buffer = planes[1].getBuffer();
        bufLenght = buffer.remaining();
        buffer.get(datas, outputpixelOffset, bufLenght);
        outputpixelOffset += bufLenght;
        //V plane
        buffer = planes[2].getBuffer();
        bufLenght = buffer.remaining();
        buffer.get(datas, outputpixelOffset, bufLenght);
   	}
   	
   	/**
   	 * 这里投机取巧直接从planes[2]中取出所有数据作为VU plane,
   	 * 因为发现planes[2]中的数据就是按照VU交叉存放的，也可以按照标准的方式从
   	 * planes[1]中取U，planes[2]中取V,但是由于是逐像素遍历，比较耗性能，
   	 * 这种方式可调用getYUV420_888Data函数实现
   	 * 
   	 * NV21\NV12实际只有两个plane, 而根据YUV_420_888定义YUV三分分量必须能够分别在
   	 * plane[0]\plane[1]\plane[2]中获取，实测华为荣耀V20, plane[1]是按照UV
   	 * 交叉存放，plane[2]是按照VU交叉存放，所以这里可直接通过plane[1]获取UV分量，
   	 * plane[2]获取VU分量
   	 * 
   	 * @param datas
   	 * @param image
   	 */
   	private void getYUV420_888DataAsNV21(byte[] datas, Image image){
   		printLog(0, "getYUV420_888DataAsNV21");
		//Rect crop = image.getCropRect();
		//int width = crop.width();
		//int height = crop.height();
		Image.Plane[] planes = image.getPlanes();

		//Y Plane
		int outputpixelOffset = 0;		    
		ByteBuffer buffer = planes[0].getBuffer();
		int bufLenght = buffer.remaining();
		buffer.get(datas, outputpixelOffset, bufLenght);
		outputpixelOffset += bufLenght;
		
		//VU Plane
		buffer = planes[2].getBuffer();
		bufLenght = buffer.remaining();
		buffer.get(datas, outputpixelOffset, bufLenght);
   	}
   	
   	private void updateFlashMode(int flashMode) {
   		printLog(0, "updateFlashMode flashMode = " + flashMode);
        switch (flashMode) {
            case FLASH_OFF:
                mCapReqBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                mCapReqBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_ON:
            	mCapReqBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            	mCapReqBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_TORCH:
            	mCapReqBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
            	mCapReqBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
                break;
            case FLASH_AUTO:
            	mCapReqBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            	mCapReqBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_RED_EYE:
            	mCapReqBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
            	mCapReqBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }
   	
   	private Matrix mPreviewToCameraTransform = null;

    private void coordinateTransformer(CameraCharacteristics chr, int prevWidth, int prevHeight) {
    	
        if (prevWidth == 0 || prevHeight == 0) {
            throw new IllegalArgumentException("previewRect");
        }
        RectF previewRect = new RectF(0.0f, 0.0f, (float)prevWidth, (float)prevHeight);
        Rect rect = chr.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        printLog(0, "coordinateTransformer sensorArraySize.width = " + rect.width() + ", sensorArraySize.height = " + rect.height());
        Integer sensorOrientation = chr.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int rotation = sensorOrientation == null ? 90 : sensorOrientation;
        RectF driverRectF = new RectF(rect);
        Integer face = mCameraFaceType;
        boolean mirrorX = face != null && face == CameraCharacteristics.LENS_FACING_FRONT;
        mPreviewToCameraTransform = previewToCameraTransform(mirrorX, rotation, previewRect,driverRectF);
    }

    /**
     * Transform a rectangle in preview view space into a new rectangle in
     * camera view space.
     * @param source the rectangle in preview view space
     * @return the rectangle in camera view space.
     */
    private MeteringRectangle toCameraSpace(Rect source) {
        RectF sRectF = new RectF(source);
        RectF result = new RectF();
        mPreviewToCameraTransform.mapRect(result, sRectF);
        return toFocusRect(result);
    }
    
    private MeteringRectangle toFocusRect(RectF rectF) {
    	Rect camRect = new Rect();
    	MeteringRectangle camMeterRect = null;
    	camRect.left = Math.round(rectF.left);
    	camRect.top = Math.round(rectF.top);
    	camRect.right = Math.round(rectF.right);
    	camRect.bottom = Math.round(rectF.bottom);
    	camMeterRect = new MeteringRectangle(camRect, MeteringRectangle.METERING_WEIGHT_MAX - 1);
    	return camMeterRect;
    }

    private Matrix previewToCameraTransform(boolean mirrorX, int sensorOrientation,
          RectF previewRect,RectF driverRectF) {
        Matrix transform = new Matrix();
        // Need mirror for front camera.
        transform.setScale(mirrorX ? -1 : 1, 1);
        // Because preview orientation is different  form sensor orientation,
        // rotate to same orientation, Counterclockwise.
        transform.postRotate(-sensorOrientation);
        // Map rotated matrix to preview rect
        transform.mapRect(previewRect);
        // Map  preview coordinates to driver coordinates
        Matrix fill = new Matrix();
        fill.setRectToRect(previewRect, driverRectF, Matrix.ScaleToFit.FILL);
        // Concat the previous transform on top of the fill behavior.
        transform.setConcat(fill, transform);
        // finally get transform matrix
        return transform;
    }
}
