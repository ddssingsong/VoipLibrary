package com.trustmobi.voip;

import android.util.Log;

import com.trustmobi.voip.voipaar.BuildConfig;

/**
 * Created by dds on 2018/5/11.
 * android_shuai@163.com
 */

public class LinLog {
    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }

    }

    public static void e(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg);
        }
    }
}
