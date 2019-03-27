package com.dds.voip.callback;

import android.app.Application;

import com.dds.voip.LinLog;
import com.dds.voip.VoipHelper;
import com.dds.voip.bean.ChatInfo;


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
    public void terminateCall(boolean isVideo, String friendId, String message) {
        LinLog.e(VoipHelper.TAG, "terminateCall friendId:" + friendId + ",message:" + message);
    }

    @Override
    public void terminateIncomingCall(boolean isVideo, String friendId, String message, boolean isMiss) {
        LinLog.e(VoipHelper.TAG, "terminateIncomingCall friendId:" + friendId + ",message:" + message + ",isMiss:" + isMiss);
    }

    @Override
    public ChatInfo getChatInfo(String userId) {
        LinLog.e(VoipHelper.TAG, "getChatInfo friendId:" + userId);
        return null;
    }

    @Override
    public ChatInfo getGroupInFo(long groupId) {
        return null;
    }

}
