package com.trustmobi.mixin.voip;


import android.util.Log;


/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 */

public class LinLog {

    public static void d(String tag, String msg) {
        Log.d(tag, msg);

    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }
}
