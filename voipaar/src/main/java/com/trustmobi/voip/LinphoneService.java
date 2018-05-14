package com.trustmobi.voip;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
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

import com.trustmobi.voip.callback.NarrowCallback;
import com.trustmobi.voip.callback.VoipCallBack;
import com.trustmobi.voip.callback.VoipCallBackDefault;
import com.trustmobi.voip.voipaar.R;

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
import org.linphone.mediastream.Version;

public class LinphoneService extends Service {
    public static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
    private static LinphoneService instance;
    private VoipCallBack callBack;
    private NarrowCallback narrowCallback;

    //悬浮窗
    private Chronometer nominator_tv;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayout;
    private View narrowView;

    private boolean isDebug;

    public static boolean isReady() {
        return instance != null;
    }

    public LinphoneService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public static LinphoneService instance() {
        if (instance != null) return instance;
        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    public void setCallBack(VoipCallBack callBack) {
        this.callBack = callBack;
    }

    public void setNarrowCallback(NarrowCallback narrowCallback) {
        this.narrowCallback = narrowCallback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isDebug = VoipHelper.getInstance().getDebug();
        LinphoneCoreFactory.instance().setLogCollectionPath(Environment.getExternalStorageDirectory().getAbsolutePath() + "/linphone");
        LinphoneCoreFactory.instance().setDebugMode(isDebug, VoipHelper.VOIP_TAG);
        LinLog.e(VoipHelper.VOIP_TAG, isDebug ? START_LINPHONE_LOGS : "Look at the log, you're too naughty");
        dumpDeviceInformation();
        LinphoneManager.createAndStart(LinphoneService.this);
        instance = this;
        initListener();
        if (VoipHelper.getInstance().isToast()) {
            Toast.makeText(this, "Voip open success", Toast.LENGTH_SHORT).show();
        }
        setCallBack(new VoipCallBackDefault(getApplication()));
    }


    public void initAuth(String domain, String stun, String username, String pwd) {
        if (instance == null) return;
        try {
            LinphoneAuthInfo authinfo = LinphoneManager.getLc().findAuthInfo(username, domain, domain);
            if (authinfo == null) {
                String identity = "sip:" + username + "@" + domain;
                String proxy = "sip:" + username + "@" + domain;
                LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(identity);
                LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
                LinphoneProxyConfig prxCfg = LinphoneManager.getLc().createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), null, true);
                proxyAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportTcp);
                prxCfg.setExpires(3600);
                prxCfg.enableQualityReporting(false);
                prxCfg.enableAvpf(false);
                prxCfg.setAvpfRRInterval(0);
                prxCfg.enableQualityReporting(false);
                LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(username, null, pwd, null, null, domain);
                LinphoneManager.getLc().addProxyConfig(prxCfg);
                LinphoneManager.getLc().addAuthInfo(authInfo);
                LinphoneManager.getLc().setDefaultProxyConfig(prxCfg);
                if (!TextUtils.isEmpty(stun)) {
                    LinphoneManager.getInstance().setStunServer(stun);
                    LinphoneManager.getInstance().setIceEnabled(true);
                }
                LinLog.e(VoipHelper.VOIP_TAG, "VoipService startLinphoneAuthInfo  -->addAuthInfo");
            } else {
                refreshRegister();
                LinLog.e(VoipHelper.VOIP_TAG, "VoipService startLinphoneAuthInfo  -->refreshRegister");
            }
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }


    public void clearAuth() {
        if (instance == null) return;
        if (LinphoneManager.getLc() != null) {
            LinphoneManager.getInstance().deleteAllAccount();

        }
    }

    public void refreshRegister() {
        if (LinphoneManager.getLc() != null) {
            LinphoneManager.getLc().refreshRegisters();
        }
    }

    private LinphoneCoreListenerBase mListener;

    private void initListener() {
        LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (instance == null) {
                    LinLog.e(VoipHelper.VOIP_TAG, "Service not ready, discarding call state change to " + state.toString());
                    return;
                }
                //=================================来电话时===================================================
                boolean invisible = false;
                if (state == LinphoneCall.State.IncomingReceived) {
                    if (callBack != null) {
                        LinphoneAddress remoteAddress = call.getRemoteAddress();
                        String userId = remoteAddress.getUserName();
                        invisible = callBack.isContactVisible(userId);
                    }
                    if (invisible) {
                        if (!LinphoneManager.getInstance().getCallGsmON()) {
                            if (LinphoneManager.getLc().getCurrentCall().getDirection() == CallDirection.Incoming) {
                                ChatActivity.openActivity(LinphoneService.this, 1);
                            }
                        }
                    } else {
                        LinphoneManager.getLc().declineCall(call, Reason.Busy);
                    }

                }

                //=================================电话接通时===================================================
                if (LinphoneCall.State.StreamsRunning == state) {
                    //如果是播出电话，并且在后台状态，就打开界面
                    if (LinphoneManager.getLc().getCurrentCall().getDirection() == CallDirection.Outgoing) {
                        ChatActivity.openActivity(LinphoneService.this, 2);
                        removeNarrow();
                    }
                }
                //=================================电话挂断时===================================================
                if (state == LinphoneCall.State.CallEnd) {
                    terminateCall(call, message);
                    removeNarrow();
                }

                //=================================出错时===================================================
                if (state == LinphoneCall.State.Error) {
                    terminateErrorCall(call, message);
                    removeNarrow();
                }
            }

            @Override
            public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

            }

            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                LinLog.e(VoipHelper.VOIP_TAG, "registrationState state:" + state + "  message: " + smessage);
                if (state == LinphoneCore.RegistrationState.RegistrationOk &&
                        LinphoneManager.getLc().getDefaultProxyConfig() != null &&
                        LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
                    LinLog.e(VoipHelper.VOIP_TAG, "Voip Login success ");
                    if (VoipHelper.getInstance().isToast()) {
                        Toast.makeText(LinphoneService.this, "Login success", Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });


    }

    private void terminateErrorCall(LinphoneCall call, String message) {
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        if (message != null && call.getErrorInfo().getReason() == Reason.NotFound) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in, userId));
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Busy) {
            displayCustomToast(getString(R.string.voice_chat_busy_toast));
        } else if (message != null && call.getErrorInfo().getReason() == Reason.BadCredentials) {
            displayCustomToast(getString(R.string.voice_chat_setmastkey_err_toast));
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Declined) {
            displayCustomToast(getString(R.string.voice_chat_friend_refused_toast));
        } else if (call.getErrorInfo().getReason() == Reason.Media) {
            displayCustomToast("不兼容的媒体参数");
        } else if (message != null) {
            displayCustomToast(getString(R.string.voice_chat_friend_refused_toast));
        }
    }


    //通话结束，分析挂断方式
    private void terminateCall(LinphoneCall call, String message) {
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        LinphoneCallLog.CallStatus callstate = call.getCallLog().getStatus();
        if (call.getDirection() == CallDirection.Outgoing) {
            //对方拒绝了您的请求.
            if (callstate == LinphoneCallLog.CallStatus.Declined) {
                if (callBack != null) {
                    Toast.makeText(this, getString(R.string.voice_chat_friend_refused_toast), Toast.LENGTH_SHORT).show();
                    callBack.terminateCall(userId, getString(R.string.voice_chat_friend_refused));
                }
            }
            //取消了呼出电话
            else if (callstate == LinphoneCallLog.CallStatus.Aborted) {
                if (callBack != null) {
                    callBack.terminateCall(userId, getString(R.string.voice_chat_cancel));
                }
            }

            // 正常通话挂断
            else if (callstate == LinphoneCallLog.CallStatus.Success) {
                int duration = call.getDuration();
                String time = formatTime((long) (duration * 1000));
                if (message.equals("Call ended")) {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateCall(userId, getString(R.string.voice_chat_time) + time);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed_user), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateCall(userId, getString(R.string.voice_chat_time) + time);
                    }
                }


            }
        } else if (call.getDirection() == CallDirection.Incoming) {
            //对方取消了或者自己长时间未接
            if (callstate == LinphoneCallLog.CallStatus.Missed) {
                if (callBack != null) {
                    if (message.equals("Call terminated")) {
                        callBack.terminateIncomingCall(userId, getString(R.string.voice_chat_time_out), true);
                    } else if (message.equals("Call ended")) {
                        callBack.terminateIncomingCall(userId, getString(R.string.voice_chat_friend_cancel), true);
                    }

                }
            }
            //呼入中自己挂断了电话
            else if (callstate == LinphoneCallLog.CallStatus.Declined) {
                if (callBack != null) {
                    callBack.terminateIncomingCall(userId, getString(R.string.voice_chat_hang_up), false);
                }
            }

            // 正常通话挂断
            else if (callstate == LinphoneCallLog.CallStatus.Success) {
                int duration = call.getDuration();
                String time = formatTime((long) (duration * 1000));
                if (message.equals("Call ended")) {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateIncomingCall(userId, getString(R.string.voice_chat_time) + time, false);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.voice_chat_succeed_user), Toast.LENGTH_SHORT).show();
                    if (callBack != null) {
                        callBack.terminateIncomingCall(userId, getString(R.string.voice_chat_time) + time, false);
                    }
                }


            }
        }


    }


    //缩小悬浮框的设置
    public void createNarrowView() {
        if (SettingsCompat.canDrawOverlays(this)) {
            try {
                narrowView = LayoutInflater.from(this).inflate(R.layout.netmonitor, null);
                mWindowManager = (WindowManager) getApplicationContext()
                        .getSystemService(WINDOW_SERVICE);
                mLayout = new WindowManager.LayoutParams();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mLayout.type = WindowManager.LayoutParams.TYPE_TOAST;
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
                                LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
                                if (call.getState() == LinphoneCall.State.StreamsRunning ||
                                        call.getState() == LinphoneCall.State.PausedByRemote ||
                                        call.getState() == LinphoneCall.State.Paused ||
                                        call.getState() == LinphoneCall.State.Pausing
                                        ) {
                                    ChatActivity.openActivity(LinphoneService.this, 2);
                                } else {
                                    if (call.getDirection() == CallDirection.Outgoing) {
                                        ChatActivity.openActivity(LinphoneService.this, 0);
                                    } else {
                                        ChatActivity.openActivity(LinphoneService.this, 1);
                                    }
                                }
                                mWindowManager.removeView(narrowView);
                            }

                            break;
                    }

                    return true;

                }
            });

            addNarrow();
        } else {
            //如果没有开启悬浮窗权限,开启悬浮窗界面
            if (null != narrowCallback) {
                narrowCallback.openSystemWindow();
            }

        }


    }

    public void addNarrow() {
        LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call != null) {
            if (mWindowManager != null && narrowView != null && mLayout != null) {
                mWindowManager.addView(narrowView, mLayout);
                LinphoneManager.getLc().enableSpeaker(true);
                if (call.getState() == LinphoneCall.State.StreamsRunning ||
                        call.getState() == LinphoneCall.State.PausedByRemote ||
                        call.getState() == LinphoneCall.State.Paused ||
                        call.getState() == LinphoneCall.State.Pausing
                        ) {
                    registerCallDurationTimer(null, call);
                } else {
                    if (call.getDirection() == CallDirection.Outgoing) {
                        nominator_tv.setText("呼出中");
                    } else {
                        nominator_tv.setText("等待接听");
                    }
                }

            }
        }


    }

    public void removeNarrow() {
        if (mWindowManager != null && narrowView != null) {
            try {
                mWindowManager.removeView(narrowView);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

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

    //添加通知
    public void sendnotification() {
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, ChatActivity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, "voip")
                        .setSmallIcon(R.drawable.answer)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!")
                        .setContentIntent(contentIntent);

        mNotifyMgr.notify(110, mBuilder.build());
    }

    @Override
    public synchronized void onDestroy() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        instance = null;
        LinphoneManager.destroy();

        super.onDestroy();
    }

    private void dumpDeviceInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE=").append(Build.DEVICE).append("\n");
        sb.append("MODEL=").append(Build.MODEL).append("\n");
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Supported ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi).append(", ");
        }
        sb.append("\n");
        LinLog.d(VoipHelper.VOIP_TAG, isDebug ? sb.toString() : "");
    }

    public void displayCustomToast(final String message) {
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setText(message);
        toast.show();
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
        if (second > 0) {
            sb.append(second < 10 ? "0" + second : "" + second);
        }
        return sb.toString();
    }

}
