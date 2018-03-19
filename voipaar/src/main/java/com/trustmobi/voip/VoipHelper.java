package com.trustmobi.voip;

import android.content.Context;
import android.content.Intent;

import com.trustmobi.voip.callback.VoipCallback;

/**
 * Created by dds on 2018/3/17 0017.
 */

public class VoipHelper {

    private VoipCallback callback;
    private boolean isDebug;
    private String mUserName;
    private String mDomain;
    private String mPwd;
    private String encrypt;

    private static class VoipHolder {
        private static VoipHelper holder = new VoipHelper();
    }

    public static VoipHelper getInstance() {
        return VoipHolder.holder;
    }


    public void setCallback(VoipCallback callback) {
        this.callback = callback;
    }

    public VoipCallback getCallback() {
        return this.callback;
    }

    public void setDebug(boolean isDebug) {
        this.isDebug = isDebug;

    }

    public boolean getDebug() {
        return isDebug;
    }

    public VoipHelper setUserName(String userName) {
        mUserName = userName;
        return this;
    }

    public String getUserName() {
        return mUserName;
    }

    public VoipHelper setDomain(String domain) {
        mDomain = domain;
        return this;
    }

    public String getDomain() {
        return mDomain;
    }

    public VoipHelper setPassword(String pwd) {
        this.mPwd = pwd;
        return this;
    }

    public String getPassword() {
        return mPwd;
    }


    public void startVoip(Context context) {
        if (mDomain == null || mUserName == null || mPwd == null) {
            throw new RuntimeException("please set domain userName and password !");
        }
        context.startService(new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
    }

    public VoipHelper setEncrypt(String encrypt) {
        this.encrypt = encrypt;
        return this;
    }

    public void callAudio(Context context, String userName) {
        LinphoneManager.getInstance().newOutgoingCall(userName);
        ChatActivity.openActivity(context, 0);
    }


}
