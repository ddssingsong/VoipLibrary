package com.trustmobi.voip.callback;

/**
 * Created by dds on 2018/5/9.
 * android_shuai@163.com
 */

public interface VoipCallBack {

    //是否联系人可用，如不可用可直接挂断电话
    boolean isContactVisible(String userId);


    //拨出的电话挂断
    void terminateCall(String friendId, String message);

    // 接收的电话挂断
    void terminateIncomingCall(String friendId, String message, boolean isMiss);


}
