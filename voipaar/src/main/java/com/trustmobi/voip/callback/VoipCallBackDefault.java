package com.trustmobi.voip.callback;

import android.app.Application;

import com.trustmobi.voip.LinLog;
import com.trustmobi.voip.VoipHelper;

/**
 * Created by dds on 2018/5/11.
 * android_shuai@163.com
 */

public class VoipCallBackDefault implements VoipCallBack {
    Application ac;

    public VoipCallBackDefault(Application ac) {
        this.ac = ac;
    }

    @Override
    public boolean isContactVisible(String userId) {
        return true;
    }

    @Override
    public void terminateCall(String friendId, String message) {
        LinLog.e(VoipHelper.VOIP_TAG, "terminateCall friendId:" + friendId + ",message:" + message);

    }

    @Override
    public void terminateIncomingCall(String friendId, String message, boolean isMiss) {
        LinLog.e(VoipHelper.VOIP_TAG, "terminateIncomingCall friendId:" + friendId + ",message:" + message + ",isMiss:" + isMiss);
    }
}
