package com.cosmos.rtmprecordpush

/**
 * Created on 2020-03-06.
 * @author jianxi[mabeijianxi@gmail.com]
 */
object Configs {
    val defaultUrl = "rtmp://172.16.3.144/live/tiantainqin"
    val width = 1980
    val height = 1040
    val videoMime = "video/avc"
    val bitRate: Int = 5 shl 20
    val frameRate: Int = 15
    val interval: Int = 10
}