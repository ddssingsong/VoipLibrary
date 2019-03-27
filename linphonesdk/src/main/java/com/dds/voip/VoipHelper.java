package com.dds.voip;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.bean.ChatInfo;
import com.dds.voip.callback.NarrowCallback;
import com.dds.voip.callback.VoipCallBack;

import org.linphone.core.LinphoneCall;

/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 * <p>
 * Voip管理类
 */

public class VoipHelper {
    public final static String TAG = "dds_voip_helper";
    private static String stun = "";
    //通话界面显示的内容
    private ChatInfo chatInfo;
    public static String friendName;
    public static boolean isInCall;
    public static boolean isVideoEnable;
    public static long mGroupId = 0;
    private boolean debug;

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }


    private static class VoipHolder {
        private static VoipHelper holder = new VoipHelper();
    }

    public static VoipHelper getInstance() {
        return VoipHolder.holder;
    }


    //先判断是否登录，再调用这个方法
    public void startVoip(Context context) {
        Intent intent = new Intent(context, VoipService.class);
        context.startService(intent);
    }

    // 登录帐号
    public void register(String userId, String password, String serverUrl) {
        if (VoipService.isReady()) {
            VoipService.instance().startLinphoneAuthInfo(stun, userId, password, serverUrl);
        }

    }

    // 拨打电话
    public void call(Context context, String callName, boolean isVideoEnable, long groupId) {
        if (VoipService.isReady()) {
            // 开始拨打电话
            LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
            if (null == call) {
                friendName = callName;
                isInCall = true;
                VoipHelper.isVideoEnable = isVideoEnable;
                mGroupId = groupId;
                OutgoingActivity.openActivity(context, isVideoEnable);
            } else {
                Toast.makeText(context, R.string.voice_chat_error_calling, Toast.LENGTH_LONG).show();
            }
        }
    }

//    public void meeting(Context context, String callName, boolean isVideoEnable, long groupId) {
//        if (VoipService.isReady()) {
//            // 开始拨打电话
//            LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
//            if (null == call) {
//                friendName = callName;
//                isInCall = true;
//                isVideoEnable = isVideoEnable;
//                mGroupId = groupId;
//                IncomingActivity.openActivity(context, isVideoEnable, groupId, callName);
//            } else {
//                Toast.makeText(context, R.string.voice_chat_error_calling, Toast.LENGTH_LONG).show();
//            }
//        }
//
//    }

    public void unRegister() {
        if (VoipService.isReady()) {
            VoipService.instance().unRegisterAuthInfo();
        }
    }

    // 关闭voip
    public void stopVoip(Context context) {
        Intent intent = new Intent(context, VoipService.class);
        context.stopService(intent);
    }

    // 开启悬浮窗
    public void createNarrow() {
        if (VoipService.isReady()) {
            VoipService.instance().createNarrowView();
        }

    }

    //是否在通话中
    public boolean isInCall(Context context) {
        if (VoipService.isReady() && LinphoneManager.isInstanciated()) {
            LinphoneCall currentCall = LinphoneManager.getLc().getCurrentCall();
            if (currentCall != null) {
                Toast.makeText(context, R.string.voice_voip_is_incall, Toast.LENGTH_SHORT).show();
                return true;
            }

        }
        return false;
    }

    //设置开启悬浮窗的回调
    private NarrowCallback narrowCallback;

    public void setNarrowCallback(NarrowCallback narrowCallback) {
        this.narrowCallback = narrowCallback;
    }

    public NarrowCallback getNarrowCallback() {
        return narrowCallback;
    }

    //设置业务逻辑的回调
    public void setVoipCallBack(VoipCallBack callBack) {
        if (VoipService.isReady()) {
            VoipService.instance().setCallBack(callBack);
        }

    }

    public ChatInfo getChatInfo() {
        return chatInfo;
    }

    public void setChatInfo(ChatInfo chatInfo) {
        this.chatInfo = chatInfo;
    }


}
