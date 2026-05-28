package com.arcvideo.opencvstudy;

public class ArcTypes {

	public static final int CAMERA_FACING_BACK  					= 	0x00;//后置摄像头
	public static final int CAMERA_FACING_FRONT 					= 	0x01;//前置摄像头
	public static final int CAMERA_FACING_NONE 					    = 	0x11;//没有方向，不设置方向

	public static final int ARC_PIXELFORMAT_UNKNOW = -1;
	
	/**
	 * pixel data in opengl texture, pointed by texture id
	 */
	public static final int ARC_PIXELFORMAT_TEXTURE = 0x1001;
	
	/**
	 * pixel data put in buffer, format is I420
	 */
	public static final int ARC_PIXELFORMAT_I420 = 0x1002;
	
	/**
	 * pixel data put in buffer, format is NV21
	 */
	public static final int ARC_PIXELFORMAT_NV21 = 0x1003;
	
	/**
	 * pixel data put in buffer, format is RGBA
	 */
	public static final int ARC_PIXELFORMAT_RGBA8888 = 0x1004;
	
	/**
	 * pixel data put in buffer, format is RGBA
	 */
	public static final int ARC_PIXELFORMAT_RGB565 = 0x1005;
	
	/**
	 * 图像数据源类型---手机设备Camera
	 */
	public static final int ARC_SOURCETYPE_PHONECAMERA = 1;
	
	/**
	 * 图像数据源类型---	手机录屏
	 */
	public static final int ARC_SOURCETYPE_SCREENCAPTURE = 2;
	
	/**
	 * 图像数据源类型---usb camera
	 */
	public static final int ARC_SOURCETYPE_USBCAMERA = 3;

	/**
	 * 图像数据源类型---手机设备Camera2
	 */
	public static final int ARC_SOURCETYPE_PHONECAMERA2 = 4;
	/**
	 * 图像数据源类型---其他数据源
	 */
	public static final int ARC_SOURCETYPE_OTHERS = 5;
	
	/**
	 * 设置视频在Surface中的显示模式
	 * FIT_IN----显示效果为保证视频所有内容都完全显示在屏幕中且不会拉伸变形，
	 *            如果视频宽高比与设备屏幕宽高比不一致则显示不会完全全屏;  
	 */
	public static final int FIT_IN = 1; 
	/**
	 * FIT_OUT---显示效果为保证视频能够全屏显示且不会拉伸变形，
	 *            如果视频宽高比与设备屏幕宽高比不一致则显示全屏，同时会有部分视频内容会超出屏幕范围从而被截掉; 
	 */
	public static final int FIT_OUT = 2;
	/**
	 * FULL_SCREEN------显示效果为保证视频所有内容都完全显示在屏幕中全屏显示，
	 *                 如果视频宽高比与设备屏幕宽高比不一致则显示会被拉伸变形; 
	 */
	public static final int FULL_SCREEN = 3;
	/**
	 * VIDEO_ENC_HW------视频硬件编码
	 *                 
	 */	
	public static final int VIDEO_ENC_HW = 0;
	/**
	 * VIDEO_ENC_SW------视频软件编码
	 *                 
	 */		
	public static final int VIDEO_ENC_SW = 1; 
	/**
	 * COLOR_SPACE_I420------视频采用软件编码时，可以设置视频输入格式COLOR_SPACE_I420
	 *                 
	 */		
	public static final int COLOR_SPACE_I420 = 0x01;
	/**
	 * COLOR_SPACE_NV21------视频采用软件编码时，可以设置视频输入格式COLOR_SPACE_NV21
	 *                 
	 */		
	public static final int COLOR_SPACE_NV21 = 0x20;
	/**
	 * VIDEO_DEC_HW------视频硬件解码
	 *
	 */
	public static final int VIDEO_DEC_HW = 0;
	/**
	 * VIDEO_DEC_SW------视频软件解码
	 *
	 */
	public static final int VIDEO_DEC_SW = 1;
	
	/**
	 * mediacodec encoder bitrate mode
	 *
	 */
	public static enum Encoder_BitrateMode{
		BITRATE_MODE_CQ, //尽最大可能保证图像质量
		BITRATE_MODE_VBR, //动态码率
		BITRATE_MODE_CBR  //固定码率
	}
	
	public static enum CombineModeType{
		MODEL_NONE,
		MODEL_RTMP, //推流模块
		MODEL_RECORDER, //录制模块
		MODEL_RTC  // rtc模块
	}

	public static enum ViewType{
		SURFACE_VIEW,
		TEXTURE_VIEW
	}

	public enum AudioDevice{
		SPEAKER_PHONE,
		WIRED_HEADSET,
		EARPIECE,
		BLUETOOTH,
		NONE
	}

	/**
	 * 水印位置类型枚举
	 */
	public static final int POSITION_LEFT_TOP 								= 	1;
	public static final int POSITION_RIGHT_TOP 								= 	2;
	public static final int POSITION_LEFT_BOTTOM							= 	3;
	public static final int POSITION_RIGHT_BOTTOM 							= 	4;
	public static final int POSITION_CENTER 								= 	5;
	
	public static final int RTC_PLAYLOAD_NONE                               =   0;
	public static final int RTC_PLAYLOAD_SEND                               =   1;
	public static final int RTC_PLAYLOAD_REC                                =   2;
	public static final int RTC_PLAYLOAD_SENDANDREC                         =   3;
	
	public static final int ARC_ERR_NONE                                    =   0;
	public static final int ARC_ERR_INVALID_ARG                             =  -1;
	public static final int ARC_ERR_INVALID_STATE                           =  -2;
	
	public static final int ARC_RTC_MODE_AUDIO                              =   1;
	public static final int ARC_RTC_MODE_VIDEO                              =   2;
	public static final int ARC_RTC_MODE_AUDIO_VIDEO                        =   0;
	
	public static final int ARC_TRANSPORT_TCP                               =   1;
	public static final int ARC_TRANSPORT_UDP                               =   0;
	
	public static final int LOG_LEVEL_NONE                                  =   0x0;
	public static final int LOG_LEVEL_ERR                                   =   0x00010000;
	public static final int LOG_LEVEL_WARN                                  =   0x00020000;
	public static final int LOG_LEVEL_INFO                                  =   0x00040000;
	public static final int LOG_LEVEL_DBG                                   =   0x00080000;
}
