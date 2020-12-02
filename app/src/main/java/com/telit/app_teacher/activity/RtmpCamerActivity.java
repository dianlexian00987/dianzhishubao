package com.telit.app_teacher.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.alex.livertmppushsdk.FdkAacEncode;
import com.alex.livertmppushsdk.RtmpSessionManager;
import com.alex.livertmppushsdk.SWVideoEncoder;
import com.telit.app_teacher.R;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class RtmpCamerActivity extends AppCompatActivity {
    private final static int ID_RTMP_PUSH_START = 100;
    private final static int ID_RTMP_PUSH_EXIT = 101;
    private final int WIDTH_DEF = 480;
    private final int HEIGHT_DEF = 640;
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

    private AudioRecord _AudioRecorder = null;
    private byte[] _RecorderBuffer = null;
    private FdkAacEncode _fdkaacEnc = null;
    private int _fdkaacHandle = 0;

    public SurfaceView _mSurfaceView = null;
    private Camera _mCamera = null;
    private boolean _bIsFront = true;
    private SWVideoEncoder _swEncH264 = null;
    private int _iDegrees = 0;

    private int _iRecorderBufferSize = 0;

    private Button _SwitchCameraBtn = null;

    private boolean _bStartFlag = false;

    private int _iCameraCodecType = android.graphics.ImageFormat.NV21;

    private byte[] _yuvNV21 = new byte[WIDTH_DEF * HEIGHT_DEF * 3 / 2];
    private byte[] _yuvEdit = new byte[WIDTH_DEF * HEIGHT_DEF * 3 / 2];

    private RtmpSessionManager _rtmpSessionMgr = null;

    private Queue<byte[]> _YUVQueue = new LinkedList<byte[]>();
    private Lock _yuvQueueLock = new ReentrantLock();// ��YUV�

    private Thread _h264EncoderThread = null;
    private Runnable _h264Runnable = new Runnable() {
        @Override
        public void run() {
            while ( _bStartFlag) {  //!_h264EncoderThread.interrupted() &&
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

                    // TODO 编码 h264
                    byte[] h264Data = _swEncH264.EncoderH264(_yuvEdit);

                    if (h264Data != null) {
                        //队列中的数据

                        _rtmpSessionMgr.InsertVideoData(h264Data);
                        if (DEBUG_ENABLE) {
                            try {
                                _outputStream.write(h264Data);
                                int iH264Len = h264Data.length;
                                Log.i(LOG_TAG, "Encode H264 len="+iH264Len);
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


    private Runnable _aacEncoderRunnable = new Runnable() {
        @Override
        public void run() {
            DataOutputStream outputStream = null;
            if (DEBUG_ENABLE) {
                File saveDir = Environment.getExternalStorageDirectory();
                String strFilename = saveDir + "/aaa.aac";
                try {
                    outputStream = new DataOutputStream(new FileOutputStream(strFilename));
                } catch (FileNotFoundException e1) {

                    e1.printStackTrace();
                }
            }

            long lSleepTime = SAMPLE_RATE_DEF * 16 * 2 / _RecorderBuffer.length;

            while (!_AacEncoderThread.interrupted() && _bStartFlag) {
                int iPCMLen = _AudioRecorder.read(_RecorderBuffer, 0, _RecorderBuffer.length); // Fill buffer
                if ((iPCMLen != _AudioRecorder.ERROR_BAD_VALUE) && (iPCMLen != 0)) {
                    if (_fdkaacHandle != 0) {
                        byte[] aacBuffer = _fdkaacEnc.FdkAacEncode(_fdkaacHandle, _RecorderBuffer);
                        if (aacBuffer != null) {
                            long lLen = aacBuffer.length;

                            _rtmpSessionMgr.InsertAudioData(aacBuffer);
                            //Log.i(LOG_TAG, "fdk aac length="+lLen+" from pcm="+iPCMLen);
                            if (DEBUG_ENABLE) {
                                try {
                                    outputStream.write(aacBuffer);
                                } catch (IOException e) {

                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } else {
                    Log.i(LOG_TAG, "######fail to get PCM data");
                }
                try {
                    Thread.sleep(lSleepTime / 10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.i(LOG_TAG, "AAC Encoder Thread ended ......");
        }
    };
    private Thread _AacEncoderThread = null;

    @SuppressLint({"InvalidWakeLockTag", "SourceLockedOrientationActivity"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_trmp_camer_main);

        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //初始化数据
        InitAll();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        _wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
    }
    private int getDispalyRotation() {
        int i = getWindowManager().getDefaultDisplay().getRotation();
        switch (i) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

    private int getDisplayOritation(int degrees, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }
    //todo  这里不是相机获取的数据

    private Camera.PreviewCallback _previewCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] YUV, Camera currentCamera) {

            //这里是真正的获取到数据
            if (!_bStartFlag) {
                return;
            }
            boolean bBackCameraFlag = true;

            byte[] yuv420 = null;

            if (_iCameraCodecType == android.graphics.ImageFormat.YV12) {
                yuv420 = new byte[YUV.length];
                _swEncH264.swapYV12toI420_Ex(YUV, yuv420, HEIGHT_DEF, WIDTH_DEF);
            } else if (_iCameraCodecType == android.graphics.ImageFormat.NV21) {
                yuv420 = _swEncH264.swapNV21toI420(YUV, HEIGHT_DEF, WIDTH_DEF);
            }

            if (yuv420 == null) {
                return;
            }
            if (!_bStartFlag) {
                return;
            }
            _yuvQueueLock.lock();
		/*	if (_YUVQueue.size() > 1) {
				_YUVQueue.clear();
			}*/
            _YUVQueue.offer(yuv420);
            _yuvQueueLock.unlock();

            //	Log.e(TAG, "onPreviewFrame: "+yuv420.length );
        }
    };

    public void InitCamera() {
        Camera.Parameters p = _mCamera.getParameters();

        Camera.Size prevewSize = p.getPreviewSize();
        Log.i(LOG_TAG, "Original Width:" + prevewSize.width + ", height:" + prevewSize.height);

        List<Camera.Size> PreviewSizeList = p.getSupportedPreviewSizes();
        List<Integer> PreviewFormats = p.getSupportedPreviewFormats();
        Log.i(LOG_TAG, "Listing all supported preview sizes");
      /*  for (Camera.Size size : PreviewSizeList) {
            Log.i(LOG_TAG, "  w: " + size.width + ", h: " + size.height);
        }*/

        Log.i(LOG_TAG, "Listing all supported preview formats");
        Integer iNV21Flag = 0;
        Integer iYV12Flag = 0;
        for (Integer yuvFormat : PreviewFormats) {
            Log.i(LOG_TAG, "preview formats:" + yuvFormat);
            if (yuvFormat == android.graphics.ImageFormat.YV12) {
                iYV12Flag = android.graphics.ImageFormat.YV12;
            }
            if (yuvFormat == android.graphics.ImageFormat.NV21) {
                iNV21Flag = android.graphics.ImageFormat.NV21;
            }
        }

        if (iNV21Flag != 0) {
            _iCameraCodecType = iNV21Flag;
        } else if (iYV12Flag != 0) {
            _iCameraCodecType = iYV12Flag;
        }
        p.setPreviewSize(HEIGHT_DEF, WIDTH_DEF);
        p.setPreviewFormat(_iCameraCodecType);
        p.setPreviewFrameRate(FRAMERATE_DEF);
        //处理视频的方向
        _mCamera.setDisplayOrientation(_iDegrees);

        p.setRotation(_iDegrees);
        _mCamera.setPreviewCallback(_previewCallback);
        _mCamera.setParameters(p);
        try {
            _mCamera.setPreviewDisplay(_mSurfaceView.getHolder());
        } catch (Exception e) {
            return;
        }
        _mCamera.cancelAutoFocus();//只有加上了这一句，才会自动对焦。

        _mCamera.startPreview();
    }

    private final class SurceCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (_mCamera!=null){
                _mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            InitCamera();
                            camera.cancelAutoFocus();//只有加上了这一句，才会自动对焦。
                        }
                    }
                });
            }else {
                _mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
               // _mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                InitCamera();
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            _iDegrees = getDisplayOritation(_iDegrees, 1);
            if (_mCamera != null) {
                InitCamera();
                return;
            }
            _mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            InitCamera();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
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
        //这个是获取视频和音频的数据推流
        _rtmpSessionMgr = new RtmpSessionManager();
        _rtmpSessionMgr.Start(_rtmpUrl);
        //打开视频视频的编码初始化
        int iFormat = _iCameraCodecType;
        _swEncH264 = new SWVideoEncoder(WIDTH_DEF, HEIGHT_DEF, FRAMERATE_DEF, BITRATE_DEF);
        _swEncH264.start(iFormat);

        _bStartFlag = true;
        //视频的编码
        _h264EncoderThread = new Thread(_h264Runnable);
        _h264EncoderThread.setPriority(Thread.MAX_PRIORITY);
        _h264EncoderThread.start();
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

    private View.OnClickListener _switchCameraOnClickedEvent = new View.OnClickListener() {
        @Override
        public void onClick(View arg0) {
            if (_mCamera == null) {
                return;
            }
            _mCamera.setPreviewCallback(null);
            _mCamera.stopPreview();
            _mCamera.release();
            _mCamera = null;

            if (_bIsFront) {
                _mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            } else {
                _mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
            _bIsFront = !_bIsFront;
            InitCamera();
        }
    };




    public Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
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

    private void RtmpStartMessage() {
        Message msg = new Message();
        msg.what = ID_RTMP_PUSH_START;
        Bundle b = new Bundle();
        b.putInt("ret", 0);
        msg.setData(b);
        mHandler.sendMessage(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
    }

    private void InitAll() {
        WindowManager wm = this.getWindowManager();

        int width = wm.getDefaultDisplay().getWidth();
        int height = wm.getDefaultDisplay().getHeight();
        int iNewWidth = (int) (height * 3.0 / 4.0);

        RelativeLayout rCameraLayout = (RelativeLayout) findViewById(R.id.cameraRelative);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        int iPos = width - iNewWidth;
        layoutParams.setMargins(0, 0, 0, 0);

        _mSurfaceView = (SurfaceView) this.findViewById(R.id.surfaceViewEx);
        _mSurfaceView.getHolder().setFixedSize(HEIGHT_DEF, WIDTH_DEF);
        _mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        _mSurfaceView.getHolder().setKeepScreenOn(true);
        _mSurfaceView.getHolder().addCallback(new SurceCallBack());
        _mSurfaceView.setLayoutParams(layoutParams);

       // InitAudioRecord();
        //切换相机
        _SwitchCameraBtn = (Button) findViewById(R.id.SwitchCamerabutton_1);
        _SwitchCameraBtn.setOnClickListener(_switchCameraOnClickedEvent);

        //开始推流
        RtmpStartMessage();

    }

    public interface  onCellNettyListener{
        void stopNetty(boolean closeNetty);
    }




    //正确的释放资源代码
    public void release(){
        if (_mCamera != null) {
            _mCamera.setPreviewCallback(null) ;
            _mCamera.stopPreview();
            _mCamera.release();
            _mCamera = null;
        }
    }
}
