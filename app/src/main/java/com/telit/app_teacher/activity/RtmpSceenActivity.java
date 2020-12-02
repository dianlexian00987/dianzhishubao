package com.telit.app_teacher.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.alex.livertmppushsdk.FdkAacEncode;
import com.alex.livertmppushsdk.RtmpSessionManager;
import com.alex.livertmppushsdk.SWVideoEncoder;
import com.telit.app_teacher.R;
import com.telit.app_teacher.screen.RESFlvData;
import com.telit.app_teacher.screen.RESFlvDataCollecter;
import com.telit.app_teacher.screen.ScreenRecorder;


import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class RtmpSceenActivity extends AppCompatActivity {
    private final static int ID_RTMP_PUSH_START = 100;
    private ScreenRecorder mVideoRecorder;
    private static final int WIDTH_DEF = 480;
    private static final int HEIGHT_DEF = 640;
    private final int FRAMERATE_DEF = 10; //20
    private final int BITRATE_DEF = 200 * 1000; //800*1000
    //采用频率
    //44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    //采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
    private final int SAMPLE_RATE_DEF = 16000;//22050
    private final int CHANNEL_NUMBER_DEF = 2;

    private final String LOG_TAG = "MainActivity";
    private final boolean DEBUG_ENABLE = false;

    private String _rtmpUrl = "rtmp://172.16.3.144/live/tiantainqin";

    PowerManager.WakeLock _wakeLock;
    private DataOutputStream _outputStream = null;

    private boolean _bIsFront = true;
    private static SWVideoEncoder _swEncH264 = null;


    private MediaProjectionManager mMediaProjectionManager;
    public static final int REQUEST_CODE_A = 10001;


    private static boolean _bStartFlag = false;

    private int _iCameraCodecType = android.graphics.ImageFormat.NV21;

    private byte[] _yuvNV21 = new byte[WIDTH_DEF * HEIGHT_DEF * 3 / 2];
    private byte[] _yuvEdit = new byte[WIDTH_DEF * HEIGHT_DEF * 3 / 2];

    private RtmpSessionManager _rtmpSessionMgr = null;

    private static Queue<byte[]> _YUVQueue = new LinkedList<byte[]>();
    private static Lock _yuvQueueLock = new ReentrantLock();// ��YUV�

    private Thread _h264EncoderThread = null;
    private Runnable _h264Runnable = new Runnable() {
        @Override
        public void run() {
            while (_bStartFlag) {  //!_h264EncoderThread.interrupted() &&
                //Log.i(LOG_TAG, _h264EncoderThread.interrupted()+":");
                int iSize = _YUVQueue.size();
                if (iSize > 0) {
                    _yuvQueueLock.lock();
                    byte[] yuvData = _YUVQueue.poll();
                    if (iSize > 9) {
                        Log.i(LOG_TAG, "###YUV Queue len=" + _YUVQueue.size() + ", YUV length=" + yuvData.length);
                    }

                    _yuvQueueLock.unlock();
                    if (yuvData == null) {
                        continue;
                    }

                    if (_bIsFront) {
                        _yuvEdit = _swEncH264.YUV420pRotate270(yuvData, HEIGHT_DEF, WIDTH_DEF);
                    } else {
                        _yuvEdit = _swEncH264.YUV420pRotate90(yuvData, HEIGHT_DEF, WIDTH_DEF);
                    }
                    byte[] h264Data = _swEncH264.EncoderH264(_yuvEdit);

                    if (h264Data != null) {
                        //编辑后  队列中的数据

                        _rtmpSessionMgr.InsertVideoData(h264Data);
                        if (DEBUG_ENABLE) {
                            try {
                                _outputStream.write(h264Data);
                                int iH264Len = h264Data.length;
                                Log.i(LOG_TAG, "Encode H264 len=" + iH264Len);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            }
            _YUVQueue.clear();
        }
    };



    @SuppressLint({"InvalidWakeLockTag", "SourceLockedOrientationActivity"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtmp_layout);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //初始化数据
        InitAll();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        _wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
    }


    private void Start() {
        if (DEBUG_ENABLE) {
            File saveDir = Environment.getExternalStorageDirectory();
            String strFilename = saveDir + "/aaa.h264";
            try {
                _outputStream = new DataOutputStream(new FileOutputStream(strFilename));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
//_rtmpSessionMgr.Start("rtmp://175.25.23.34/live/12345678");
        //_rtmpSessionMgr.Start("rtmp://192.168.0.110/live/12345678");
        _rtmpSessionMgr = new RtmpSessionManager();
        _rtmpSessionMgr.Start(_rtmpUrl);

     /*   int iFormat = _iCameraCodecType;
        _swEncH264 = new SWVideoEncoder(WIDTH_DEF, HEIGHT_DEF, FRAMERATE_DEF, BITRATE_DEF);
        _swEncH264.start(iFormat);*/

        _bStartFlag = true;
        //视频的编码
   /*     _h264EncoderThread = new Thread(_h264Runnable);
        _h264EncoderThread.setPriority(Thread.MAX_PRIORITY);
        _h264EncoderThread.start();*/
        //音频的编码
	/*	_AudioRecorder.startRecording();
		_AacEncoderThread = new Thread(_aacEncoderRunnable);
		_AacEncoderThread.setPriority(Thread.MAX_PRIORITY);
		_AacEncoderThread.start();*/

    }

    private void Stop() {
        _bStartFlag = false;

        //_AacEncoderThread.interrupt();
        _h264EncoderThread.interrupt();

        //_AudioRecorder.stop();
        _swEncH264.stop();

        _rtmpSessionMgr.Stop();

        _yuvQueueLock.lock();
        _YUVQueue.clear();
        _yuvQueueLock.unlock();

        if (DEBUG_ENABLE) {
            if (_outputStream != null) {
                try {
                    _outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            int ret;
            switch (msg.what) {
                case ID_RTMP_PUSH_START: {
                    //开始推流
                    Start();
                    break;
                }
            }
        }
    };

   public RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
        @Override
        public void collect(RESFlvData flvData, int type) {
            //这里是返回后添加的数据
            Log.i("qin", "collect: "+flvData.size);
            _yuvQueueLock.lock();
            //把编码后数据
            _rtmpSessionMgr.InsertVideoData(flvData.byteBuffer);
           // _YUVQueue.offer(flvData.byteBuffer);
            _yuvQueueLock.unlock();
        }
    };

    private void RtmpStartMessage() {
        Message msg = new Message();
        msg.what = ID_RTMP_PUSH_START;
        Bundle b = new Bundle();
        b.putInt("ret", 0);
        msg.setData(b);
        mHandler.sendMessage(msg);
    }

    private void InitAll() {

        InitMPManager();
        //开始推流
        RtmpStartMessage();
    }


    /**
     * 初始化MediaProjectionManager
     * **/
    private void InitMPManager(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        }
        //开始录屏
        StartScreenCapture();
    }

    /**
     * 开始截屏
     * **/
    private void StartScreenCapture(){
        Intent captureIntent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQUEST_CODE_A);

    }




    /**
     *
     * **/
    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        try {
            //录屏的数据
            MediaProjection mediaProjection = null;
                mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);

            if(mediaProjection == null){
                Toast.makeText(this,"程序发生错误:MediaProjection@1",Toast.LENGTH_SHORT).show();
                return;
            }


            mVideoRecorder = new ScreenRecorder(collecter, RESFlvData.VIDEO_WIDTH,
                    RESFlvData.VIDEO_HEIGHT, RESFlvData.VIDEO_BITRATE,
                    1, mediaProjection);
            mVideoRecorder.start();

        }
        catch (Exception e){

        }
    }
}
