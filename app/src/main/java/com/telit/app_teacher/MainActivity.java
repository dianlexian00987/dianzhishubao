package com.telit.app_teacher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.telit.app_teacher.activity.RtmpCamerActivity;
import com.telit.app_teacher.customNetty.MsgUtils;
import com.telit.app_teacher.customNetty.SimpleClientListener;
import com.telit.app_teacher.customNetty.SimpleClientNetty;
import com.telit.app_teacher.server.MultServer;
import com.telit.app_teacher.utils.SharedPreferenceUtil;
import com.wildma.idcardcamera.camera.IDCardCamera;

import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity implements SimpleClientListener {

    private TextView tv_shiwu;
    private TextView tv_tooping;
    private MediaProjectionManager mMediaProjectionManager;
    public static final int REQUEST_CODE_A = 10001;
    private final static int PERMISSIONS_OK = 10001;
    public static final String rtmpUrl="rtmp://172.16.3.144/live/tiantainqin";

    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    private TextView tv_class_name;
    private Button bt_start;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPerm();

        setContentView(R.layout.activity_main);
        tv_shiwu = (Button) findViewById(R.id.tv_shiwu);
        tv_tooping = (Button) findViewById(R.id.tv_tooping);
        //收到班级
        tv_class_name = (TextView) findViewById(R.id.tv_class_name);
        //拍照讲解
        bt_start = (Button) findViewById(R.id.bt_start);

        //通讯服务接口
        SimpleClientNetty.getInstance().setSimpleClientListener(MainActivity.this);

        tv_shiwu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //开启一个服务 这个是实物展台
                startService(new Intent(MainActivity.this, MultServer.class));
            }
        });

        //录屏 推流
        tv_tooping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RtmpCamerActivity.class));
               // startActivity(new Intent(MainActivity.this, RtmpScreenOneActivity.class));
            }
        });

        bt_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IDCardCamera.create(MainActivity.this).openCamera(IDCardCamera.TYPE_IDCARD_FRONT);
            }
        });
        SharedPreferenceUtil.getInstance(this);

    }

    private ScheduledExecutorService messageExecutorService;


    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_OK:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //这里已经获取到了摄像头的权限，想干嘛干嘛了可以
                    init();
                } else {
                   // showWaringDialog();
                }
                break;
            default:
                break;
        }
    }



    private void requestPerm() {
        if (Build.VERSION.SDK_INT>22) {
            if (!checkPermissionAllGranted(PERMISSIONS_STORAGE)) {
                //先判断有没有权限 ，没有就在这里进行权限的申请
                ActivityCompat.requestPermissions(MainActivity.this,
                        PERMISSIONS_STORAGE, PERMISSIONS_OK);
            }else{
                init();
            }
        }else{
            init();
        }
    }

    private void init() {

    }






    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }
    //在线
    @Override
    public void onLine() {
        // 发送推流的地址给 教师端
        SimpleClientNetty.getInstance().sendMsgToServer(MsgUtils.HEAD_SCREEN_CAST, rtmpUrl);

    }

    @Override
    public void offLine() {

    }
    //接收到消息
    @Override
    public void receiveData(String msgInfo) {

    }
}
