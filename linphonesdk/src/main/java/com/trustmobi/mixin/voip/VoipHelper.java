package com.trustmobi.mixin.voip;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.dds.tbs.linphonesdk.R;
import com.trustmobi.mixin.voip.bean.ChatInfo;
import com.trustmobi.mixin.voip.callback.NarrowCallback;
import com.trustmobi.mixin.voip.callback.VoipCallBack;

import org.linphone.core.LinphoneCall;

import static com.trustmobi.mixin.voip.VoipService.NOTIFY_OUTGOING;

/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 * <p>
 * Voip管理类
 */

public class VoipHelper {
    public final static String TAG = "dds_voip_helper";
    private String stun = null;
    //通话界面显示的内容
    private ChatInfo chatInfo;
    public static String friendName;
    public static String randomKey;
    public static boolean isInCall;
    public static boolean isVideoEnale;

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
    public void call(Context context, String callName, boolean isVideoEnable, String randomData) {
        if (VoipService.isReady()) {
            // 开始拨打电话
            LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
            if (null == call) {
                friendName = callName;
                randomKey = randomData;
                isInCall = true;
                isVideoEnale = isVideoEnable;
                VoipActivity.openActivity(context, NOTIFY_OUTGOING);
            } else {
                Toast.makeText(context, R.string.voice_chat_error_calling, Toast.LENGTH_LONG).show();
            }
        }
    }

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
    public boolean isInCall() {
        if (VoipService.isReady() && LinphoneManager.isInstanciated()) {
            LinphoneCall currentCall = LinphoneManager.getLc().getCurrentCall();
            if (currentCall != null) {
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
