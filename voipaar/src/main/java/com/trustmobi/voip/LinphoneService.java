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
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.Toast;

import com.trustmobi.voip.callback.NarrowCallback;
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
import org.linphone.core.LinphoneNatPolicy;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Version;

public class LinphoneService extends Service {
    public static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
    private static LinphoneService instance;

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

    @Override
    public void onCreate() {
        super.onCreate();
        isDebug = VoipHelper.getInstance().getDebug();
        LinphoneCoreFactory.instance().setLogCollectionPath(Environment.getExternalStorageDirectory().getAbsolutePath() + "/linphone");
        LinphoneCoreFactory.instance().setDebugMode(isDebug, VoipHelper.VOIP_TAG);
        Log.i(VoipHelper.VOIP_TAG, isDebug ? START_LINPHONE_LOGS : "Look at the log, you're too naughty");
        dumpDeviceInformation();
        LinphoneManager.createAndStart(LinphoneService.this);
        instance = this;
        initListener();
        if (VoipHelper.getInstance().isToast()) {
            Toast.makeText(this, "Voip open success", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String userName = intent.getStringExtra("username");
        String pwd = intent.getStringExtra("password");
        String domain = intent.getStringExtra("domain");
        String stun = intent.getStringExtra("String");
        initAuth(domain, stun, userName, pwd);
        return super.onStartCommand(intent, flags, startId);
    }

    public void initAuth(String domain, String stun, String username, String pwd) {
        if (instance == null) return;
        try {
            String identity = "sip:" + username + "@" + domain;
            String proxy = "sip:" + username + "@" + domain;
            LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(identity);
            LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
            LinphoneProxyConfig prxCfg = LinphoneManager.getLc().createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), null, true);
            proxyAddr.setTransport(LinphoneAddress.TransportType.LinphoneTransportUdp);
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


    private LinphoneCoreListenerBase mListener;

    private void initListener() {
        LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (instance == null) {
                    Log.e(VoipHelper.VOIP_TAG, "Service not ready, discarding call state change to " + state.toString());
                    return;
                }
                Log.d(VoipHelper.VOIP_TAG, "callState:" + state.toString() + ",call direction:" + call.getDirection() + ",message:" + message);
                //来电话
                if (state == LinphoneCall.State.IncomingReceived) {
                    if (!LinphoneManager.getInstance().getCallGsmON()) {
                        if (LinphoneManager.getLc().getCurrentCall().getDirection() == CallDirection.Incoming) {
                            ChatActivity.openActivity(LinphoneService.this, 1);
                        }

                    }
                }

                //挂断电话
                if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.CallReleased || state == LinphoneCall.State.Error) {
                    if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null && LinphoneManager.getLc().getCallsNb() == 0) {

                    }
                    removeNarrow();
                }

                if (state == LinphoneCall.State.CallEnd && call.getCallLog().getStatus() == LinphoneCallLog.CallStatus.Missed) {
                    LinphoneAddress address = call.getRemoteAddress();

                }

                if (state == LinphoneCall.State.StreamsRunning) {
                    //如果是播出电话，并且在后台状态，就打开界面
                    if (LinphoneManager.getLc().getCurrentCall().getDirection() == CallDirection.Outgoing) {
                        removeNarrow();
                        ChatActivity.openActivity(LinphoneService.this, 2);
                    }
                } else {
                    //log


                }
            }

            @Override
            public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

            }

            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                Log.e(VoipHelper.VOIP_TAG, "registrationState state:" + state + "  message: " + smessage);
                if (state == LinphoneCore.RegistrationState.RegistrationOk &&
                        LinphoneManager.getLc().getDefaultProxyConfig() != null &&
                        LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
                    Log.e(VoipHelper.VOIP_TAG, "Voip Login success ");
                    if (VoipHelper.getInstance().isToast()) {
                        Toast.makeText(LinphoneService.this, "Login success", Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });


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
            NarrowCallback callback = VoipHelper.getInstance().getNarrowCallback();
            if (null != callback) {
                callback.openSystemWindow();
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
        Log.i(VoipHelper.VOIP_TAG, isDebug ? sb.toString() : "");
    }
}
