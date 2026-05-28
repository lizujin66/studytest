package com.arcvideo.opencvstudy;

public interface ArcFrameDataCallBack {
	/**
	 * Frame数据更新时调用
	 * 用于使用者获取frame数据
	 * @param vFrame
	 */
	public abstract void onFrame(ArcVFrame vFrame);
}
