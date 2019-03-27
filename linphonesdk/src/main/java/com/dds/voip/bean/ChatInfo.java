package com.dds.voip.bean;

/**
 * Created by dds on 2018/5/16.
 * android_shuai@163.com
 */

public class ChatInfo {
    private String remoteAvatar;
    private String remoteNickName;
    private int defaultAvatar;

    public String getRemoteAvatar() {
        return remoteAvatar;
    }

    public void setRemoteAvatar(String remoteAvatar) {
        this.remoteAvatar = remoteAvatar;
    }

    public String getRemoteNickName() {
        return remoteNickName;
    }

    public void setRemoteNickName(String remoteNickName) {
        this.remoteNickName = remoteNickName;
    }


    @Override
    public String toString() {
        return "ChatInfo{" +
                "remoteAvatar='" + remoteAvatar + '\'' +
                ", remoteNickName='" + remoteNickName + '\'' +
                '}';
    }

    public int getDefaultAvatar() {
        return defaultAvatar;
    }

    public void setDefaultAvatar(int defaultAvatar) {
        this.defaultAvatar = defaultAvatar;
    }
}
