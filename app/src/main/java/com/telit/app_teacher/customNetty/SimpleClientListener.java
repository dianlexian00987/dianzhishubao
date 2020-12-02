package com.telit.app_teacher.customNetty;

/**
 * author: qzx
 * Date: 2019/4/28 10:13
 */
public interface SimpleClientListener {
    void onLine();

    void offLine();

    void receiveData(String msgInfo);
}
