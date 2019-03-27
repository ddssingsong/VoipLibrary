package com.dds.voip.callback;

import com.dds.voip.bean.ChatInfo;

/**
 * Created by dds on 2018/5/9.
 * android_shuai@163.com
 */

public interface VoipCallBack {

    //是否联系人可用，如不可用可直接挂断电话
    boolean isContactVisible(String userId);


    //拨出的电话挂断
    void terminateCall(boolean isVideo,String friendId, String message);

    // 接收的电话挂断
    void terminateIncomingCall(boolean isVideo,String friendId, String message, boolean isMiss);


    //获取需要在界面上显示的用户信息
    ChatInfo getChatInfo(String userId);

    ChatInfo getGroupInFo(long groupId);

 }
