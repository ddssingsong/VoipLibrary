package com.trustmobi.voip;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.Toast;

import com.trustmobi.voip.voipaar.R;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
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
        //设置日志
        LinphoneCoreFactory.instance().setLogCollectionPath(Environment.getExternalStorageDirectory().getAbsolutePath() + "linphone");
        LinphoneCoreFactory.instance().setDebugMode(VoipHelper.getInstance().getDebug(), "dds");
        Log.i("dds", START_LINPHONE_LOGS);
        dumpDeviceInformation();


        LinphoneManager.createAndStart(LinphoneService.this);
        instance = this;
        initListener();
    }


    //缩小悬浮框的设置
    public void createNarrowView() {
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


    }

    public void addNarrow() {
        LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call != null) {
            if (mWindowManager != null && narrowView != null) {
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


    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) narrowView.findViewById(R.id.netmonitor_tv);
        }
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    public void removeNarrow() {
        if (mWindowManager != null && narrowView != null) {
            mWindowManager.removeView(narrowView);
        }

    }

    private LinphoneCoreListenerBase mListener;

    private void initListener() {
        LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (instance == null) {
                    Log.e("dds", "Service not ready, discarding call state change to " + state.toString());
                    return;
                }
                //来电话
                if (state == LinphoneCall.State.IncomingReceived) {
                    if (!LinphoneManager.getInstance().getCallGsmON()) {
                        ChatActivity.openActivity(LinphoneService.this, 1);
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

                } else {

                }
            }

            @Override
            public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

            }

            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                Log.e("dds", "registrationState state:" + state + "  message: " + smessage);
                if (state == LinphoneCore.RegistrationState.RegistrationOk &&
                        LinphoneManager.getLc().getDefaultProxyConfig() != null &&
                        LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
                    Log.e("dds", "voip登录成功");
                    Toast.makeText(LinphoneService.this, "登陆成功", Toast.LENGTH_SHORT).show();
                }
            }
        });


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
            sb.append(abi + ", ");
        }
        sb.append("\n");
        Log.i("dds", sb.toString());
    }
}
