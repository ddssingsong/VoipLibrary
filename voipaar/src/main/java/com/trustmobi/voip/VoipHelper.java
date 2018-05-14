package com.trustmobi.voip;

import android.content.Context;
import android.content.Intent;

import com.trustmobi.voip.callback.NarrowCallback;
import com.trustmobi.voip.callback.VoipCallBack;

/**
 * Created by dds on 2018/3/17 0017.
 */

public class VoipHelper {

    public static final String VOIP_TAG = "dds_voip";

    private NarrowCallback narrowCallback;
    private boolean isDebug;
    private boolean isToast;


    private static class VoipHolder {
        private static VoipHelper holder = new VoipHelper();
    }

    public static VoipHelper getInstance() {
        return VoipHolder.holder;
    }

    //开启voip服务
    public void startVoipService(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class);
        context.startService(intent);
    }

    //登录
    public void register(String domain, String stun, String username, String pwd) {
        if (LinphoneService.isReady()) {
            LinphoneService.instance().initAuth(domain, stun, username, pwd);
        }

    }

    //删除帐号
    public void unRegister() {
        LinphoneService.instance().clearAuth();
    }


    //拨打语音电话
    public void callAudio(Context context, String userName) {
        LinphoneManager.getInstance().newOutgoingCall(userName);
        ChatActivity.openActivity(context, 0);
    }

    //拨打视频电话
    public void callVideo(Context context, String userName) {
        LinphoneManager.getInstance().newOutgoingCall(userName);
        ChatActivity.openActivity(context, 0);
    }

    // 开启悬浮窗
    public void createNarrow(Context context) {
        if (SettingsCompat.canDrawOverlays(context)) {
            if (LinphoneService.isReady()) {
                LinphoneService.instance().createNarrowView();
            }
        }

    }





    //设置开启悬浮窗的回调
    public void setNarrowCallback(NarrowCallback narrowCallback) {
        if (LinphoneService.isReady()) {
            LinphoneService.instance().setNarrowCallback(narrowCallback);
        }
    }

    //设置业务逻辑的回调
    public void setVoipCallBack(VoipCallBack callBack) {
        if (LinphoneService.isReady()) {
            LinphoneService.instance().setCallBack(callBack);
        }

    }


    public VoipHelper setDebug(boolean isDebug) {
        this.isDebug = isDebug;
        return this;

    }

    public boolean getDebug() {
        return isDebug;
    }

    public boolean isToast() {
        return isToast;
    }

    public void setToast(boolean toast) {
        isToast = toast;
    }


}
