package com.trustmobi.voip;

import android.content.Context;
import android.content.Intent;

import com.trustmobi.voip.callback.DisplayCallback;
import com.trustmobi.voip.callback.NarrowCallback;

/**
 * Created by dds on 2018/3/17 0017.
 */

public class VoipHelper {

    private DisplayCallback displayCallback;
    private NarrowCallback narrowCallback;
    private boolean isDebug;
    private String mUserName;
    private String mDomain;
    private String mPwd;
    private String encrypt;
    private String stunServer;


    private static class VoipHolder {
        private static VoipHelper holder = new VoipHelper();
    }

    public static VoipHelper getInstance() {
        return VoipHolder.holder;
    }


    public void setDisplayCallback(DisplayCallback displayCallback) {
        this.displayCallback = displayCallback;
    }

    public DisplayCallback getDisplayCallback() {
        return this.displayCallback;
    }

    public void setNarrowCallback(NarrowCallback narrowCallback) {
        this.narrowCallback = narrowCallback;
    }

    public NarrowCallback getNarrowCallback() {
        return narrowCallback;
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

    public String getStunServer() {
        return stunServer;
    }

    public VoipHelper setStunServer(String stunServer) {
        this.stunServer = stunServer;
        return this;
    }

    public void startVoip(Context context) {
        if (mDomain == null || mUserName == null || mPwd == null) {
            throw new RuntimeException("please set domain userName and password !");
        }
        context.startService(new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
    }

    public void stopVoip(Context context) {
        context.stopService(new Intent(Intent.ACTION_MAIN).setClass(context, LinphoneService.class));
    }

    public VoipHelper setEncrypt(String encrypt) {
        this.encrypt = encrypt;
        return this;
    }

    public void callAudio(Context context, String userName) {
        LinphoneManager.getInstance().newOutgoingCall(userName, false);
        ChatActivity.openActivity(context, 0);
    }

    public void callVideo(Context context, String userName) {
        LinphoneManager.getInstance().newOutgoingCall(userName, true);
        ChatActivity.openActivity(context, 0);
    }

}
