package com.arcvideo.opencvstudy;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecoder {
    private static final String TAG = "VideoDecoder";
    private Context mContext;
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;
    private ImageReader mImageReader;
    private ArcFrameDataCallBack mFrameCallback;
    private volatile boolean mIsRunning = false;
    private Thread mDecodeThread;
    private int mVideoWidth;
    private int mVideoHeight;

    public VideoDecoder(Context context) {
        mContext = context;
    }

    public void setFrameCallback(ArcFrameDataCallBack callback) {
        this.mFrameCallback = callback;
    }

    public void startDecode(Uri videoUri) {
        if (mIsRunning) {
            stopDecode();
        }
        
        mIsRunning = true;
        mDecodeThread = new Thread(() -> {
            try {
                initDecoder(videoUri);
                decodeLoop();
            } catch (Exception e) {
                Log.e(TAG, "Decode error", e);
            } finally {
                release();
            }
        });
        mDecodeThread.start();
    }

    public void stopDecode() {
        mIsRunning = false;
        if (mDecodeThread != null) {
            try {
                mDecodeThread.join(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mDecodeThread = null;
        }
    }

    private void initDecoder(Uri videoUri) throws IOException {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(mContext, videoUri, null);

        int videoTrackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                videoTrackIndex = i;
                break;
            }
        }

        if (videoTrackIndex < 0) {
            throw new RuntimeException("No video track found in " + videoUri);
        }

        mExtractor.selectTrack(videoTrackIndex);
        mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

        // 初始化 ImageReader，格式为 YUV_420_888
        mImageReader = ImageReader.newInstance(mVideoWidth, mVideoHeight, ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image);
                image.close();
            }
        }, null);

        String mime = format.getString(MediaFormat.KEY_MIME);
        mDecoder = MediaCodec.createDecoderByType(mime);
        Surface surface = mImageReader.getSurface();
        mDecoder.configure(format, surface, null, 0);
        mDecoder.start();
    }

    private void decodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        while (mIsRunning) {
            if (!isEOS) {
                int inIndex = mDecoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = mDecoder.getInputBuffer(inIndex);
                    int sampleSize = mExtractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        mDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                        mExtractor.advance();
                    }
                }
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                // 控制播放速度，按真实时间戳同步
                while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

                // 释放给Surface (这会触发ImageReader的onImageAvailable)
                mDecoder.releaseOutputBuffer(outIndex, true);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Format changed
            }
        }
    }

    private void processImage(Image image) {
        if (mFrameCallback != null && mIsRunning) {
            byte[] nv21Bytes = YUV_420_888toNV21(image);
            ArcVFrame vFrame = new ArcVFrame(
                    nv21Bytes,
                    -1,
                    image.getWidth(),
                    image.getHeight(),
                    image.getWidth(),
                    ArcTypes.ARC_PIXELFORMAT_NV21,
                    0,
                    image.getTimestamp()
            );
            mFrameCallback.onFrame(vFrame);
        }
    }

    private byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int rowStride = image.getPlanes()[0].getRowStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        // 复制Y通道
        int pos = 0;
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize);
            pos = ySize;
        } else {
            byte[] yRow = new byte[rowStride];
            for (int r = 0; r < height; r++) {
                yBuffer.get(yRow, 0, rowStride);
                System.arraycopy(yRow, 0, nv21, pos, width);
                pos += width;
            }
        }

        // 复制VU通道转为NV21 (交错VU)
        byte[] vRow = new byte[uvRowStride];
        byte[] uRow = new byte[uvRowStride];
        for (int r = 0; r < height / 2; r++) {
            vBuffer.get(vRow, 0, Math.min(vBuffer.remaining(), uvRowStride));
            uBuffer.get(uRow, 0, Math.min(uBuffer.remaining(), uvRowStride));

            for (int c = 0; c < width / 2; c++) {
                nv21[pos++] = vRow[c * uvPixelStride];
                nv21[pos++] = uRow[c * uvPixelStride];
            }
        }

        return nv21;
    }

    private void release() {
        if (mDecoder != null) {
            try {
                mDecoder.stop();
            } catch (Exception e){}
            mDecoder.release();
            mDecoder = null;
        }
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }
}
