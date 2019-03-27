package com.dds.voip;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.Toast;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.bean.ChatInfo;
import com.dds.voip.callback.NarrowCallback;
import com.dds.voip.callback.VoipCallBack;
import com.dds.voip.callback.VoipCallBackDefault;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Reason;

import java.util.Timer;
import java.util.TimerTask;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.dds.voip.LinphoneManager.NOT_ANSWER_TIME;
import static com.dds.voip.LinphoneManager.getLc;
import static com.dds.voip.OutgoingActivity.IS_VIDEO;
import static com.dds.voip.VoipActivity.CHAT_TYPE;
import static com.dds.voip.VoipActivity.VOIP_CALL;
import static com.dds.voip.VoipActivity.VOIP_INCOMING;
import static com.dds.voip.VoipActivity.VOIP_OUTGOING;

/**
 * Created by dds on 2018/5/3.
 * Voip 服务
 */

public class VoipService extends Service {
    public static VoipService instance;
    private VoipCallBack callBack;


    private NotificationManager mNM;
    public static final int NOTIFY_INCOMING = 100;
    public static final int NOTIFY_OUTGOING = 200;
    public static final int NOTIFY_CALL = 300;
    private static final String id = "channel_1";
    private static final String name = "channel_name_1";

    public static VoipService instance() {
        if (instance != null) return instance;
        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    public static boolean isReady() {
        return instance != null;
    }

    public void setCallBack(VoipCallBack callBack) {
        this.callBack = callBack;
    }

    public VoipCallBack getCallBack() {
        return callBack;
    }

    private boolean isDebug = false;

    public static void stopCall(Context context) {
        Intent intent = new Intent(context, VoipService.class);
        intent.putExtra("type", "stop");
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        //设置日志
        isDebug = VoipHelper.getInstance().isDebug();
        LinphoneCoreFactory.instance().setDebugMode(isDebug, "dds_voip");
        LinphoneCoreFactory.instance().setLogCollectionPath(Environment.getExternalStorageDirectory().getAbsolutePath() + "/trustmobi_voip");
        mWindowManager = (WindowManager) getApplicationContext()
                .getSystemService(WINDOW_SERVICE);
        //开启基本配置
        LinphoneManager.createAndStart(this);
        instance = this;
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        initListener();
        initReceiver();
        setCallBack(new VoipCallBackDefault(getApplication()));
        if (isDebug) {
            displayCustomToast("open success");
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String type = intent.getStringExtra("type");
            if (!TextUtils.isEmpty(type)) {
                if (type.equals("stop")) {
                    cancelNotification(NOTIFY_OUTGOING);
                    cancelNotification(NOTIFY_INCOMING);
                    cancelNotification(NOTIFY_CALL);
                    return super.onStartCommand(intent, flags, startId);
                }
            }
        }

        refreshRegister();
        return super.onStartCommand(intent, flags, startId);
    }

    private LinphoneCoreListenerBase mListener;

    private void initListener() {
        getLc().addListener(mListener = new LinphoneCoreListenerBase() {
            @Override
            public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

            }

            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                if (state == LinphoneCore.RegistrationState.RegistrationOk && getLc().getDefaultProxyConfig() != null && getLc().getDefaultProxyConfig().isRegistered()) {
                    LinLog.e("dds_voip", "login success");
                    if (isDebug) {
                        displayCustomToast("login success");
                    }
                } else {
                    LinLog.e("dds_voip", "login failed,message:" + smessage);
                }
            }

            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (instance == null) {
                    LinLog.e(VoipHelper.TAG, "Service not ready, discarding call state change to " + state.toString());
                    return;
                }
                LinphoneCall currentCall = getLc().getCurrentCall();
                if (currentCall != null && call != currentCall) {
                    return;
                }

                if (state == LinphoneCall.State.IncomingReceived || state == LinphoneCall.State.OutgoingInit) {
                    LinphoneAddress remoteAddress = call.getRemoteAddress();
                    String userId = remoteAddress.getUserName();
                    if (callBack != null) {
                        ChatInfo chatInfo = callBack.getChatInfo(userId);
                        VoipHelper.getInstance().setChatInfo(chatInfo);
                    }
                }

                if (state == LinphoneCall.State.OutgoingInit) {
                    startTimer();
                    sendNotification(NOTIFY_OUTGOING, getString(R.string.voice_chat_notifi_content));
                }
                //=================================来电话时===================================================
                boolean invisible = true;
                if (state == LinphoneCall.State.IncomingReceived) {
                    if (callBack != null) {
                        LinphoneAddress remoteAddress = call.getRemoteAddress();
                        String userId = remoteAddress.getUserName();
                        invisible = callBack.isContactVisible(userId);
                    }
                    if (invisible) {
                        if (!LinphoneManager.getInstance().getCallGsmON()) {
                            if (getLc().getCurrentCall().getDirection() == CallDirection.Incoming) {
                                VoipHelper.isInCall = true;
                                VoipActivity.openActivity(VoipService.this, VOIP_INCOMING);
                                sendNotification(NOTIFY_INCOMING, getString(R.string.voice_chat_notifi_content));
                            }
                        }
                    } else {
                        getLc().declineCall(call, Reason.Busy);
                    }

                }

                //=================================电话接通时===================================================
                if (LinphoneCall.State.StreamsRunning == state) {
                    VoipHelper.isInCall = true;
                    //设置通知栏
                    cancelNotification(NOTIFY_OUTGOING);
                    cancelNotification(NOTIFY_INCOMING);
                    sendNotification(NOTIFY_CALL, getString(R.string.voice_chat_notifi_content));
                    stopTimer();
                    //如果是播出电话，并且在后台状态，就打开界面
                    if (getLc().getCurrentCall().getDirection() == CallDirection.Outgoing) {
                        VoipActivity.openActivity(VoipService.this, VOIP_CALL);
                        removeNarrow();
                    }
                }
                //=================================电话挂断时===================================================
                if (state == LinphoneCall.State.CallEnd) {
                    terminateCall(call, message);
                    removeNarrow();
                    stopTimer();
                    cancelNotification(NOTIFY_OUTGOING);
                    cancelNotification(NOTIFY_INCOMING);
                    cancelNotification(NOTIFY_CALL);
                    VoipHelper.friendName = "";
                    VoipHelper.mGroupId = 0;
                    destroyOverlay();
                }

                //=================================出错时===================================================
                if (state == LinphoneCall.State.Error) {
                    terminateErrorCall(call, message);
                    removeNarrow();
                    stopTimer();
                    cancelNotification(NOTIFY_OUTGOING);
                    cancelNotification(NOTIFY_INCOMING);
                    cancelNotification(NOTIFY_CALL);
                    VoipHelper.friendName = "";
                    VoipHelper.mGroupId = 0;
                    destroyOverlay();
                }

            }

        });

    }

    private void terminateErrorCall(LinphoneCall call, String message) {
        VoipHelper.isInCall = false;
        LinLog.e("dds_test", call.getDirection() + "," + call.getState().toString() + "," + message);
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        boolean isVideoEnable = call.getCurrentParams().getVideoEnabled();
        if (call.getDirection() == CallDirection.Outgoing) {
            if (callBack != null) {
                if (message.contains("Busy")) {
                    callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_busy));
                } else {
                    callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_cancel));
                }

            }
        }
    }

    //通话结束，分析挂断方式
    private void terminateCall(LinphoneCall call, String message) {
        VoipHelper.isInCall = false;
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        LinphoneCallLog.CallStatus callstate = call.getCallLog().getStatus();
        boolean isVideoEnable = call.getCurrentParams().getVideoEnabled();
        boolean isRemoteVideoEnabe = call.getRemoteParams().getVideoEnabled();
        LinLog.e("dds_test", call.getDirection() + "," + callstate.toString() + "," + message);
        if (call.getDirection() == CallDirection.Outgoing) {
            //对方拒绝了您的请求.
            if (callstate == LinphoneCallLog.CallStatus.Declined) {
                if (callBack != null) {
                    if (current < (NOT_ANSWER_TIME - 2)) {
                        Toast.makeText(this, getString(R.string.voice_chat_friend_refused_toast), Toast.LENGTH_SHORT).show();
                        callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_friend_refused));
                    } else {
                        Toast.makeText(this, getString(R.string.voice_chat_no_answer), Toast.LENGTH_SHORT).show();
                        callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_no_answer));
                    }

                }
            }
            //取消了呼出电话
            else if (callstate == LinphoneCallLog.CallStatus.Aborted) {
                if (callBack != null) {
                    callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_cancel));
                }
            }

            // 正常通话挂断
            else if (callstate == LinphoneCallLog.CallStatus.Success) {
                int duration = call.getDuration();
                String time = formatTime((long) (duration * 1000));
                if (message.equals("Call ended")) {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_time) + time);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed_user), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateCall(isVideoEnable, userId, getString(R.string.voice_chat_time) + time);
                    }
                }


            }


        } else if (call.getDirection() == CallDirection.Incoming) {
            //对方取消了或者自己长时间未接
            if (callstate == LinphoneCallLog.CallStatus.Missed) {
                if (callBack != null) {
                    if (message.equals("Call terminated")) {
                        callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_time_out), true);
                    } else if (message.equals("Call ended")) {
                        callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_friend_cancel), true);
                    }

                }
            } else if (callstate == LinphoneCallLog.CallStatus.Aborted) {
                if (callBack != null) {
                    callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_friend_cancel), true);

                }
            }
            //呼入中自己挂断了电话
            else if (callstate == LinphoneCallLog.CallStatus.Declined) {
                if (callBack != null) {
                    callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_hang_up), false);
                }
            }

            // 正常通话挂断
            else if (callstate == LinphoneCallLog.CallStatus.Success) {
                int duration = call.getDuration();
                String time = formatTime((long) (duration * 1000));
                if (message.equals("Call ended")) {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_time) + time, false);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed_user), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, getString(R.string.voice_chat_time) + time, false);
                    }
                }


            }
        }


    }


    // 在服务器注册用户，授权用户
    public void startLinphoneAuthInfo(String stun, String username, String pwd, String domain) {
        if (instance == null) return;
        LinLog.e("startLinphoneAuthInfo", "VoipService startLinphoneAuthInfo");
        try {
            LinphoneAuthInfo authinfo = getLc().findAuthInfo(username, null, domain);
            if (authinfo == null) {
                String identity = "sip:" + username + "@" + domain;
                String proxy = "sip:" + username + "@" + domain;
                LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(identity);
                LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
                proxyAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportTcp);
                LinphoneProxyConfig prxCfg = getLc().createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), null, true);
                prxCfg.setExpires(1800);
                prxCfg.enableQualityReporting(false);
                prxCfg.enableAvpf(false);
                prxCfg.setAvpfRRInterval(0);
                LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(username, null, pwd, null, null, domain);
                getLc().addProxyConfig(prxCfg);
                getLc().addAuthInfo(authInfo);
                getLc().setDefaultProxyConfig(prxCfg);
                if (!TextUtils.isEmpty(stun)) {
                    LinphoneManager.getInstance().setStunServer(stun);
                    LinphoneManager.getInstance().setIceEnabled(true);
                }
                LinLog.e("startLinphoneAuthInfo", "VoipService startLinphoneAuthInfo  -->addAuthInfo");
            } else {
                refreshRegister();
                LinLog.e("startLinphoneAuthInfo", "VoipService startLinphoneAuthInfo  -->refreshRegister");
            }

        } catch (LinphoneCoreException e) {
            LinLog.e("startLinphoneAuthInfo", "VoipService startLinphoneAuthInfo  -->error LinphoneCoreException");
        }
    }

    public void unRegisterAuthInfo() {
        if (instance == null) return;
        if (getLc() != null) {
            LinphoneManager.getInstance().deleteAllAccount();

        }
    }

    public void refreshRegister() {
        if (getLc() != null) {
            getLc().refreshRegisters();
        }
    }

    //悬浮窗
    private Chronometer nominator_tv;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayout;
    private View narrowView;
    private LinphoneOverlay mOverlay;

    //缩小悬浮框的设置
    public void createNarrowView() {
        if (SettingsCompat.canDrawOverlays(this)) {
            try {
                narrowView = LayoutInflater.from(this).inflate(R.layout.voip_netmonitor, null);
                mLayout = new WindowManager.LayoutParams();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mLayout.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mLayout.type = WindowManager.LayoutParams.TYPE_TOAST;
            } else {
                mLayout.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
            mLayout.format = PixelFormat.RGBA_8888;
            mLayout.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_FULLSCREEN;
            mLayout.gravity = Gravity.LEFT | Gravity.TOP;
            mLayout.width = WindowManager.LayoutParams.WRAP_CONTENT;
            mLayout.height = WindowManager.LayoutParams.WRAP_CONTENT;
            mLayout.x = this.getResources().getDisplayMetrics().widthPixels;
            mLayout.y = 0;
            nominator_tv = narrowView.findViewById(R.id.netmonitor_tv);
            narrowView.setOnTouchListener(new View.OnTouchListener() {
                float downX = 0;
                float downY = 0;
                int oddOffsetX = 0;
                int oddOffsetY = 0;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getX();
                            downY = event.getY();
                            oddOffsetX = mLayout.x;
                            oddOffsetY = mLayout.y;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float moveX = event.getX();
                            float moveY = event.getY();
                            mLayout.x += (moveX - downX) / 3;
                            mLayout.y += (moveY - downY) / 3;
                            if (narrowView != null) {
                                mWindowManager.updateViewLayout(narrowView, mLayout);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            int newOffsetX = mLayout.x;
                            int newOffsetY = mLayout.y;
                            if (Math.abs(newOffsetX - oddOffsetX) <= 20 && Math.abs(newOffsetY - oddOffsetY) <= 20) {
                                //开启Activity
                                openActivity();
                            }

                            break;
                    }

                    return true;

                }
            });
            addNarrow();
        } else {
            //如果没有开启悬浮窗权限,开启悬浮窗界面
            NarrowCallback narrowCallback = VoipHelper.getInstance().getNarrowCallback();
            if (null != narrowCallback) {
                narrowCallback.openSystemWindow();
            }

        }


    }

    //打开通话界面
    private void openActivity() {
        LinphoneCall call = getLc().getCurrentCall();
        if (call != null) {
            if (call.getState() == LinphoneCall.State.StreamsRunning ||
                    call.getState() == LinphoneCall.State.Connected ||
                    call.getState() == LinphoneCall.State.PausedByRemote ||
                    call.getState() == LinphoneCall.State.Paused ||
                    call.getState() == LinphoneCall.State.Pausing
            ) {
                VoipActivity.openActivity(VoipService.this, VOIP_CALL);
            } else if (call.getState() == LinphoneCall.State.IncomingReceived ||
                    call.getState() == LinphoneCall.State.CallIncomingEarlyMedia) {
                if (call.getDirection() == CallDirection.Incoming) {
                    VoipActivity.openActivity(VoipService.this, VOIP_INCOMING);
                }
            }
        } else {
            removeNarrow();
        }


    }

    public synchronized void addNarrow() {
        LinphoneCall call = getLc().getCurrentCall();
        if (call != null) {
            try {
                if (mWindowManager != null && narrowView != null && mLayout != null) {
                    mWindowManager.addView(narrowView, mLayout);
                    getLc().enableSpeaker(true);
                    if (call.getState() == LinphoneCall.State.StreamsRunning ||
                            call.getState() == LinphoneCall.State.PausedByRemote ||
                            call.getState() == LinphoneCall.State.Paused ||
                            call.getState() == LinphoneCall.State.Pausing
                    ) {
                        registerCallDurationTimer(null, call);
                    } else {
                        if (call.getDirection() == CallDirection.Outgoing) {
                            nominator_tv.setText(R.string.voice_chat_calling);
                        } else {
                            nominator_tv.setText(R.string.voice_chat_float_waitting);
                        }
                    }

                }
            } catch (Exception e) {
                //如果进程被杀 Unable to add window -- token null is not valid; is your activity running?
                removeNarrow();

            }

        }


    }

    public synchronized void removeNarrow() {
        if (mWindowManager != null && narrowView != null) {
            try {
                mWindowManager.removeView(narrowView);
                narrowView = null;
            } catch (Exception e) {
                //
            }

        }

    }

    public void createOverlay() {
        if (mOverlay != null) destroyOverlay();
        LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null || !call.getCurrentParams().getVideoEnabled()) return;
        if (SettingsCompat.canDrawOverlays(this)) {
            try {
                mOverlay = new LinphoneOverlay(this);
                WindowManager.LayoutParams params = mOverlay.getWindowManagerLayoutParams();
                params.x = 0;
                params.y = 0;
                mWindowManager.addView(mOverlay, params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            //如果没有开启悬浮窗权限,开启悬浮窗界面
            NarrowCallback narrowCallback = VoipHelper.getInstance().getNarrowCallback();
            if (null != narrowCallback) {
                narrowCallback.openSystemWindow();
            }
        }

    }

    public void destroyOverlay() {
        if (mWindowManager != null && mOverlay != null) {
            try {
                mWindowManager.removeViewImmediate(mOverlay);
                mOverlay.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mOverlay = null;
    }

    //注册时间计数器
    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) narrowView.findViewById(R.id.netmonitor_tv);
        }
        if (timer == null) {
            return;
        }
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public synchronized void onDestroy() {
        cancelNotification(NOTIFY_OUTGOING);
        cancelNotification(NOTIFY_INCOMING);
        cancelNotification(NOTIFY_CALL);
        destroyOverlay();
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {

            try {
                lc.declineCall(lc.getCurrentCall(), Reason.NotAnswered);
                lc.terminateAllCalls();
            } catch (Exception e) {
                e.printStackTrace();
            }
            lc.removeListener(mListener);
        }
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }

        instance = null;
        LinphoneManager.destroy();
        VoipHelper.isInCall = false;
        stopTimer();
        super.onDestroy();
    }

    public static String formatTime(Long ms) {
        Integer ss = 1000;
        Integer mi = ss * 60;
        Integer hh = mi * 60;
        Integer dd = hh * 24;

        Long day = ms / dd;
        Long hour = (ms - day * dd) / hh;
        Long minute = (ms - day * dd - hour * hh) / mi;
        Long second = (ms - day * dd - hour * hh - minute * mi) / ss;

        StringBuffer sb = new StringBuffer();
        if (day > 0) {
            sb.append(day < 10 ? "0" + day : "" + day).append(":");
        }
        if (hour > 0) {
            sb.append(hour < 10 ? "0" + hour : "" + hour).append(":");
        }
        sb.append(minute < 10 ? "0" + minute : "" + minute).append(":");
        if (second >= 0) {
            sb.append(second < 10 ? "0" + second : "" + second);
        }
        return sb.toString();
    }

    public void displayCustomToast(final String message) {
        displayCustomToast(message, Toast.LENGTH_SHORT);
    }

    public void displayCustomToast(final String message, int duration) {
        Toast toast = Toast.makeText(this, message, duration);
        toast.setGravity(Gravity.CENTER, 0, 100);
        toast.setDuration(duration);
        toast.setText(message);
        toast.show();
    }

    //====================================增加用户体验的逻辑=======================================

    private static final int NOT_ANSWER_REMINDER_1 = 1001;//对方长时间未接
    private static final int NOT_ANSWER_REMINDER_2 = 1002;//对方长时间未接，即将挂断

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case NOT_ANSWER_REMINDER_1:
                    displayCustomToast(getString(R.string.voice_chat_no_answer_toast), Toast.LENGTH_LONG);
                    break;
                case NOT_ANSWER_REMINDER_2:
                    displayCustomToast(getString(R.string.voice_chat_no_answer_tips), Toast.LENGTH_LONG);
                    break;
            }
        }
    };
    private Timer timer;
    private int current = 0;

    private void startTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                current++;
                if (current == (LinphoneManager.NOT_ANSWER_TIME / 2 - 5)) {
                    //提示对方手机可能不在身边
                    handler.sendEmptyMessage(NOT_ANSWER_REMINDER_1);
                }
                if (current == (LinphoneManager.NOT_ANSWER_TIME / 2 + 5)) {
                    //提示对方手机可能不在身边
                    handler.sendEmptyMessage(NOT_ANSWER_REMINDER_1);
                }
                if (current == LinphoneManager.NOT_ANSWER_TIME - 5) {
                    handler.sendEmptyMessage(NOT_ANSWER_REMINDER_2);
                }
            }
        }, 0, 1000);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            current = 0;
            timer = null;
        }
        if (handler != null) {
            handler.removeMessages(NOT_ANSWER_REMINDER_1);
            handler.removeMessages(NOT_ANSWER_REMINDER_2);
        }

    }


    //添加通知
    public void sendNotification(int type, String content) {
        mNM.cancel(type);
        createNotificationChannel();
        Notification notification = getNotification(type, getString(R.string.voice_chat), content).build();
        mNM.notify(type, notification);
    }

    //消除通知
    public void cancelNotification(int type) {
        mNM.cancel(type);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mNM.deleteNotificationChannel(id);
        }

    }

    public void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
            mNM.deleteNotificationChannel(id);
            mNM.createNotificationChannel(channel);
        }

    }

    public NotificationCompat.Builder getNotification(int type, String title, String content) {
        Intent resultIntent;
        if (type == VOIP_OUTGOING) {
            resultIntent = new Intent(this, OutgoingActivity.class);
            resultIntent.putExtra(IS_VIDEO, LinphoneManager.getLc().getCurrentCall().getCurrentParams().getVideoEnabled());
        } else {
            resultIntent = new Intent(this, VoipActivity.class);
            resultIntent.putExtra(CHAT_TYPE, type);
        }

        //resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), id)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(resultPendingIntent);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(R.drawable.voip_answer);
        } else {
            builder.setSmallIcon(R.drawable.voip_answer);
        }
        return builder;

    }


    //注册广播接收者
    private BroadcastReceiver broadcastReceiver;

    private void initReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                        if (LinphoneUtils.isNetConnected(VoipService.this)) {
                            refreshRegister();
                        } else {
                            refreshRegister();
                            if (getLc() != null) {
                                hangUp();
                            }
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, filter);

    }

    private void hangUp() {
        LinphoneCore lc = getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }


}