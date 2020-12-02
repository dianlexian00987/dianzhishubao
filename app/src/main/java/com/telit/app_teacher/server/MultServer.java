package com.telit.app_teacher.server;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.telit.app_teacher.Myapp;
import com.telit.app_teacher.activity.RtmpCamerActivity;
import com.telit.app_teacher.bean.ServerIpInfo;
import com.telit.app_teacher.customNetty.SimpleClientNetty;
import com.telit.app_teacher.utils.SharedPreferenceUtil;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MultServer extends Service {
    public static final String TAG="MultServer";

    private volatile ConcurrentHashMap<String, ServerIpInfo> ServerIpInfos1 = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, ServerIpInfo> ServerIpInfos2 = new ConcurrentHashMap<>();

    private CopyOnWriteArrayList<ServerIpInfo> valueServerIpInfos = new CopyOnWriteArrayList<>();

    private static final String Multicast_IP = "224.5.6.7";
    private static final int Multicast_Port = 37656;
    private ExecutorService executorService;
    private MulticastSocket multicastSocket;
    private WifiManager.MulticastLock multicastLock;
    private Timer timerTask;
    private CountDownLatch endLatch;
    private static boolean isShow = false;
    private static final int Operator_Success_Two = 5;
    private static final int Operator_Err = 4;
    private static boolean is_join_Multicast = true;

    //存当前数据的集合
    ConcurrentHashMap<String, ServerIpInfo> currentServerInfos = new ConcurrentHashMap<>();

    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Operator_Success_Two:
                    valueServerIpInfos.clear();
                    valueServerIpInfos.clear();
                    ConcurrentHashMap<String, ServerIpInfo> currentServerInfos = (ConcurrentHashMap<String, ServerIpInfo>) msg.obj;
                    if (currentServerInfos == null) {

                        return;
                    }
                    Log.i(TAG, "handleMessage123: " + Operator_Success_Two);
                    //遍历map
                    for (Map.Entry<String, ServerIpInfo> entry : currentServerInfos.entrySet()) {
                        valueServerIpInfos.add(entry.getValue());
                    }

                    String ip=valueServerIpInfos.get(0).getDeviceIp();
                    int port = Integer.valueOf(valueServerIpInfos.get(0).getDevicePort());
                    Log.i(TAG, "handleMessage: "+ip+port);
                    SharedPreferenceUtil.getInstance(Myapp.getInstance()).setString("socketIp",ip);
                    SharedPreferenceUtil.getInstance(Myapp.getInstance()).setInt("port",port);
                    //建立连接
                    SimpleClientNetty.getInstance().reConnect();
                  //  Intent intent = new Intent(MultServer.this, RtmpCamerActivity.class);
                    Intent intent = new Intent(MultServer.this, RtmpCamerActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
                    intent.putExtra("ip",ip);
                    intent.putExtra("port",port);
                    MultServer.this.startActivity(intent);
                    stopSelf();
                    break;
            }
        }
    } ;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //开起服务回去地址ip
        is_join_Multicast = true;
        isShow = true;
        //上一次集合的数据
        ServerIpInfos2.clear();
        multicastSocket = createMulticastGroupAndJoin(Multicast_IP, Multicast_Port);
        executorService = Executors.newSingleThreadExecutor();

        executorService.execute(new Runnable() {
            @Override
            public synchronized void run() {
                Log.i(TAG, "run: 0619" + 22222222);
                while (is_join_Multicast) {
                    Log.i(TAG, "run: 0619" + 1111111);
                    try {
                        getDatashows();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        //5s后把数据给清除了 获取新的数据    主要就是5s 比对一下两个集合是不是一样
        timerTask = new Timer();
        timerTask.schedule(new TimerTask() {
            @Override
            public void run() {
                //当前的集合
                recycleInfoView();
            }
        }, 2500, 4000);
    }


    private synchronized void recycleInfoView() {


        currentServerInfos.clear();
        currentServerInfos.putAll(ServerIpInfos1);
    /*    Iterator<Map.Entry<String, ServerIpInfo>> entryIterator = ServerIpInfos1.entrySet().iterator();
        while (entryIterator.hasNext()){
            entryIterator.remove();
        }*/
        synchronized (MultServer.class){
            ServerIpInfos1.clear();
        }

        Log.i(TAG, "run: wwwwwServerIpInfos1=" + currentServerInfos);
        Log.i(TAG, "run: wwwwwServerIpInfos2=" + ServerIpInfos2);
        Log.i(TAG, "run: wwwwwServerIpInfos: "+isMapEaquse(currentServerInfos,ServerIpInfos2));
        if ( !isMapEaquse(currentServerInfos,ServerIpInfos2)&& currentServerInfos.size()>=1) {
            Log.i(TAG, "onPause: 222222222222我是刷新数据");
            if (mHandler != null) {
                Message message = Message.obtain();
                message.obj = currentServerInfos;
                message.what = Operator_Success_Two;
                mHandler.sendMessage(message);
            }
        }else {
            //这里主要是可能教师端关闭了
            if (mHandler != null &&  currentServerInfos.size()==0) {
                Message message = Message.obtain();
                message.obj = null;
                message.what = Operator_Success_Two;
                mHandler.sendMessage(message);
            }
        }
        //上一次的集合
        ServerIpInfos2.clear();
        ServerIpInfos2.putAll(currentServerInfos);
    }


    //判断两个map  是不是一样
    private static boolean isMapEaquse(ConcurrentHashMap<String, ServerIpInfo> currentServerInfos,
                                       ConcurrentHashMap<String, ServerIpInfo> serverIpInfos2){
        if (currentServerInfos.size()!=serverIpInfos2.size())return false;
        Iterator<String> iterator = currentServerInfos.keySet().iterator();

        if (iterator.hasNext()){
            String next = iterator.next();
            Log.i(TAG, "isMapEaquse: "+next);
            if (serverIpInfos2.containsKey(next)){
                return true;
            }
        }
        return false;
    }


    private synchronized void getDatashows() {
        String message = recieveData(multicastSocket, Multicast_IP);//接收组播组传来的消息
        if (!TextUtils.isEmpty(message)) {
            try {
                JSONObject jsonObject = new JSONObject(message);
                String className = jsonObject.optString("className");
                String ip = jsonObject.optString("address");
                String port = jsonObject.optString("port");
                // String serviceName = jsonObject.optString("serviceName");


                ServerIpInfo serverIpInfo = new ServerIpInfo();
                serverIpInfo.setClassName(className);
                serverIpInfo.setDevicePort(port);
                serverIpInfo.setDeviceIp(ip);
                serverIpInfo.setClassName(className);
                //多线程操作这个集合  添加一个等待
                synchronized (MultServer.this){
                    ServerIpInfos1.put(ip, serverIpInfo);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public MulticastSocket createMulticastGroupAndJoin(String groupurl, int port) // 创建一个组播组并加入此组的函数
    {
        try {
            InetAddress group = InetAddress.getByName(groupurl); // 设置组播组的地址为239.0.0.0
            MulticastSocket socket = new MulticastSocket(port); // 初始化MulticastSocket类并将端口号与之关联

            //  socket.setTimeToLive(1); // 设置组播数据报的发送范围为本地网络
            socket.setSoTimeout(10000); // 设置套接字的接收数据报的最长时间
            socket.joinGroup(group); // 加入此组播组
            //socket.setLoopbackMode(false);
            return socket;
        } catch (Exception e1) {
            System.out.println("Error: " + e1); // 捕捉异常情况
            return null;
        }
    }


    public synchronized String recieveData(MulticastSocket socket, String groupurl) {
        String message;
        try {
            InetAddress group = InetAddress.getByName(groupurl);
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length, group, Multicast_Port);

            socket.receive(packet); // 通过MulticastSocket实例端口从组播组接收数据

            // 将接受的数据转换成字符串形式
            message = new String(packet.getData(), 0, packet.getLength());

            Log.i(TAG, "recieveData:44444 " + message);
        } catch (Exception e1) {
            Log.i(TAG, "recieveData: " + e1);
            message = "Error: " + e1;
            if (is_join_Multicast) {
                synchronized (MultServer.class){
                    ServerIpInfos1.clear();
                }
                ServerIpInfos2.clear();
                if (mHandler != null) {
                    Message message1 = Message.obtain();
                    message1.what = Operator_Success_Two;
                    message1.obj = null;
                    mHandler.sendMessage(message1);
                }
            }
        }
        return message;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
