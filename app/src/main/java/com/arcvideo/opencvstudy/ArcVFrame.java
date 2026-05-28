package com.arcvideo.opencvstudy;

import java.nio.ByteBuffer;

public class ArcVFrame {

	public byte[] mFrameBuffer; //视频帧数据
	public ByteBuffer mFrameByteBuffer; //视频数据
	public int mFrameTextureId = -1;//含有帧数据的opengl texture id
	public int mFrameWidth; //视频宽
	public int mFrameHeight; // 视频高
	public int mFrameStride; // stride
	public int mColorFormat; //颜色空间
	public int mRotation; //旋转角度
	public long mTimeStmp; //时间搓
	
	/**
	 * Video Frame结构体，存储视屏帧数据及基本信息，
	 * data和textureId分别表示数据两种形式，
	 * data表示是实际的内存数据，textureId是opengl中存放数据的纹理id;
	 * 这两个变量根据实际数据格式通常只设置一个即可，另一个设置默认值
	 * 
	 * @param data  内存数据，不需要则设置为null
	 * @param textureId opengl纹理id, 不需要则设置为-1
	 * @param width 视屏帧宽
	 * @param height 视屏帧高
	 * @param stride 对齐后的视屏帧宽
	 * @param colorFormat 颜色格式
	 * @param rotate    旋转角度
	 * @param timeStamp 时间搓
	 */
	public ArcVFrame(byte[] data, int textureId, int width, int height, int stride
			, int colorFormat, int rotate, long timeStamp) {
		mFrameBuffer = data;
		mFrameByteBuffer = null;
		mFrameTextureId = textureId;
		mFrameWidth = width;
		mFrameHeight = height;
		mFrameStride = stride;
		mColorFormat = colorFormat;
		mRotation = rotate;
		mTimeStmp = timeStamp;
	}
}
