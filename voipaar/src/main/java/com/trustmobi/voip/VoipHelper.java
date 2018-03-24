package com.trustmobi.voip;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.trustmobi.voip.callback.NarrowCallback;

import org.linphone.core.LinphoneAuthInfo;

/**
 * Created by dds on 2018/3/17 0017.
 */

public class VoipHelper {

    public static final String VOIP_TAG = "dds";

    private NarrowCallback narrowCallback;
    private boolean isDebug;
    private boolean isToast;
    private String encrypt;
    private String decrypt;


    private static class VoipHolder {
        private static VoipHelper holder = new VoipHelper();
    }

    public static VoipHelper getInstance() {
        return VoipHolder.holder;
    }

    //开启voip服务
    public void startVoipService(Context context) {
        if (!LinphoneService.isReady()) {
            context.startService(new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
        } else {
            Log.e(VOIP_TAG, "VOIP is running and does not need to be reopened");
        }

    }

    //关闭Voip服务
    public void stopVoipService(Context context) {
        context.stopService(new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
    }

    //注册账号
    public void initAuth(String domain, String stun, String username, String pwd) {
        LinphoneService.instance().initAuth(domain, stun, username, pwd);
    }

    //清除账号
    public void clearAuth() {
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


    public String[] getAuth() {
        if (!LinphoneManager.isInstanciated()) return null;
        LinphoneAuthInfo authInfo = LinphoneManager.getInstance().getAuthInfo();
        if (authInfo == null) return null;
        String str[] = new String[3];
        str[0] = authInfo.getDomain();
        str[1] = authInfo.getUsername();
        str[2] = authInfo.getPassword();
        return str;
    }


    public void setNarrowCallback(NarrowCallback narrowCallback) {
        this.narrowCallback = narrowCallback;
    }

    public NarrowCallback getNarrowCallback() {
        return narrowCallback;
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
