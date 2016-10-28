package richard.example.com.screenrecord;

import android.annotation.TargetApi;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2016/10/27.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class ScreenRecord
        extends Thread {
    private static final String TAG = "ScreenRecorder";

    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private String mDstPath;
    private MediaProjection mediaProjection;

    //parameters for encoder
    private static final String MIME_TYPE = "video/avc"; //H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30; //30 fps
    private static final int IFRAME_INTERVAL = 10; //10 seconds between I-frames
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mEncoder;
    private MediaCodec mediaCodec;
    private Surface mSurface;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    private int mVideoTrackIndex = -1;
    private AtomicBoolean mQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private VirtualDisplay mVirtualDisplay;

    public ScreenRecord(int width, int height, int bitrate,
                        int dpi, MediaProjection mp, String dstPath){
        super(TAG);
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;
        mediaProjection = mp;
        mDstPath = dstPath;
    }

    public ScreenRecord(MediaProjection mp)
    {
        this(640, 480, 2000000,1,mp, "/sdcard/test.mp4");
    }

    //Stop task
    public final void quit(){
        mQuit.set(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void prepareEncoder() throws IOException{
        //mWidth和mHeight是视频的尺寸，这个尺寸不能超过视频采集时采集到的尺寸，否则会直接crash
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth,mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);


        //设置码率，通常码率越高，视频越清晰，但是对应的视频也越大，这个值我默认设置成了2000000，也就是通常所说的2M
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        //设置帧率，通常这个值越高，视频会显得越流畅，一般默认我设置成30，你最低可以设置成24，不要低于这个值，低于24会明显卡顿
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);

        //IFRAME_INTERVAL是指的帧间隔，这是个很有意思的值，它指的是，关键帧的间隔时间。通常情况下，你设置成多少问题都不大。
        //比如你设置成10，那就是10秒一个关键帧。但是，如果你有需求要做视频的预览，那你最好设置成1
        //因为如果你设置成10，那你会发现，10秒内的预览都是一个截图
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,IFRAME_INTERVAL);

        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface:" + mSurface);
        mEncoder.start();

//        mediaCodec = MediaCodec.createEncoderByType("video/avc");
//        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 320, 240);
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
//        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
//        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
//        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        mediaCodec.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void release(){
        if(mEncoder != null){
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if(mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }

        if(mediaProjection != null){
            mediaProjection.stop();
        }

        if(mMuxer != null){
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void resetOutputFormat(){
        //should happen before receiving buffers, and should only happen once
        if(mMuxerStarted){
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat  = mEncoder.getOutputFormat();

        Log.i(TAG, "output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.d(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encodeToVideoTrack(int index)
    {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

        if((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0){
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status
            //Ignore it.
            Log.d(TAG, "ingoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }

        if(mBufferInfo.size == 0 ){
            Log.d(TAG, "info.size == 0, drop it");
            encodedData  = null;
        } else
        {
            Log.d(TAG, "got buffer, info: size= " + mBufferInfo.size
                + ", presentationTimeUs =" + mBufferInfo.presentationTimeUs
                + ",offset=" + mBufferInfo.offset);
        }

        if(encodedData != null){
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset  + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex,encodedData,mBufferInfo);
            Log.i(TAG, "sent " + mBufferInfo.size + "bytes to muxer...");
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void recordVirtualDisplay()
    {
        while (!mQuit.get())
        {
            int index = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.i(TAG, "dequeue output buffer index=" + index);
            if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                resetOutputFormat();
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER){
                Log.d(TAG, "retrieving buffers time out!");
                try {
                    //wait 10ms
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else if(index >= 0){
                if(!mMuxerStarted){
                    throw  new IllegalStateException("MediaMuxer dose not call addTrack");
                }
                encodeToVideoTrack(index);
                mEncoder.releaseOutputBuffer(index, false);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run(){
        try
        {
            try {
                prepareEncoder();
                mMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mVirtualDisplay = mediaProjection.createVirtualDisplay(
                    TAG + "-display",
                    mWidth,mHeight,mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSurface, null,null);
            Log.d(TAG,"created virtual display: " + mVirtualDisplay);
            recordVirtualDisplay();
        }finally {
            release();
        }
    }
}
