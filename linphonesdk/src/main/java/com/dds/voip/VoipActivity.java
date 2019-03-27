package com.dds.voip;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.frgment.CallAudioFragment;
import com.dds.voip.frgment.CallVideoFragment;
import com.dds.voip.frgment.VideoPreViewFragment;
import com.dds.voip.utils.StatusBarCompat;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.Reason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 */

public class VoipActivity extends AppCompatActivity implements ComButton.onComClick, View.OnClickListener {

    // 来电话
    private LinearLayout voip_chat_incoming;
    private ComButton voip_accept;
    private ComButton voip_hang_up;
    // 去电话--视频
    private RelativeLayout voip_chat_outgoing;
    private ComButton voip_cancel;
    private ComButton voip_chat_switch_audio;

    //语音聊天
    private LinearLayout voip_voice_chatting;
    private ComButton voip_chat_mute;
    private ComButton voip_chat_cancel;
    private ComButton voip_chat_hands_free;

    //视频聊天
    private LinearLayout voip_video_chatting;
    private ComButton voip_chat_switch_voice;
    private ComButton voip_chat_cancel_video;
    private ComButton voip_chat_switch_camera;
    private ComButton voip_chat_mute2;


    private LinphoneCall mCall;
    private LinphoneCoreListenerBase mListener;
    private HomeWatcherReceiver homeWatcherReceiver;

    private CallAudioFragment audioCallFragment;
    private CallVideoFragment videoCallFragment;
    private VideoPreViewFragment videoPreViewFragment;

    private boolean isSpeakerEnabled = false, isMicMuted = false;


    private int chatType;
    public static final String CHAT_TYPE = "chatType";
    public static final int VOIP_INCOMING = 100;
    public static final int VOIP_OUTGOING = 200;
    public static final int VOIP_CALL = 300;


    // chatType  0 播出电话 1 接听电话  2 通话中
    public static void openActivity(Context context, int chatType) {
        openActivity(context, chatType, false);
    }

    // isNoAnimation 是否有启动动画
    public static void openActivity(Context context, int chatType, boolean isNoAnimation) {
        if (VoipService.isReady()) {
            Intent intent = new Intent(context, VoipActivity.class);
            intent.putExtra(CHAT_TYPE, chatType);
            if (context instanceof Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
                ((Activity) context).overridePendingTransition(0, 0);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            if (OutgoingActivity.outgoingActivity != null) {
                OutgoingActivity.outgoingActivity.finish();
            }
        }
    }


    // 拨打时为了等待对方启动，需要等待2秒再进行拨打
    public static final int CALL = 0x001;
    private VoipHandler voipHandler = new VoipHandler();


    private static class VoipHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CALL:
                    VoipHelper.isInCall = false;
                    LinphoneManager.getInstance().newOutgoingCall(VoipHelper.friendName, VoipHelper.isVideoEnable);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarCompat.compat(this);
        //设置锁屏状态下也能亮屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.voip_activity_chat);
        initView();
        initVar();
        initListener();
        initReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        initVar();
        initListener();
        super.onNewIntent(intent);
    }

    private void initReceiver() {
        homeWatcherReceiver = new HomeWatcherReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(homeWatcherReceiver, filter);

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1000);
            }
        }
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            isSpeakerEnabled = lc.isSpeakerEnabled();
            voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.voip_hands_free : R.drawable.voip_btn_voice_hand_free);
            isMicMuted = lc.isMicMuted();
            voip_chat_mute.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);
            lookupCalling();
        }
        VoipService.instance().removeNarrow();


    }

    @Override
    protected synchronized void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected synchronized void onDestroy() {
        if (homeWatcherReceiver != null) {
            unregisterReceiver(homeWatcherReceiver);
        }
        if (voipHandler != null) {
            voipHandler.removeMessages(CALL);
        }
        VoipHelper.isInCall = false;
        super.onDestroy();
    }

    private void initView() {
        voip_chat_incoming = (LinearLayout) findViewById(R.id.voip_chat_incoming);
        voip_voice_chatting = (LinearLayout) findViewById(R.id.voip_voice_chatting);
        voip_chat_mute = (ComButton) findViewById(R.id.voip_chat_mute);
        voip_chat_cancel = (ComButton) findViewById(R.id.voip_chat_cancel);
        voip_chat_hands_free = (ComButton) findViewById(R.id.voip_chat_hands_free);
        voip_accept = (ComButton) findViewById(R.id.voip_accept);
        voip_hang_up = (ComButton) findViewById(R.id.voip_hang_up);
        voip_video_chatting = findViewById(R.id.voip_video_chatting);
        voip_chat_switch_voice = findViewById(R.id.voip_chat_switch_voice);
        voip_chat_cancel_video = findViewById(R.id.voip_chat_cancel_video);
        voip_chat_switch_camera = findViewById(R.id.voip_chat_switch_camera);
        voip_chat_outgoing = findViewById(R.id.voip_chat_outgoing);
        voip_cancel = findViewById(R.id.voip_cancel);
        voip_chat_switch_audio = findViewById(R.id.voip_chat_switch_audio);
        voip_chat_mute2 = findViewById(R.id.voip_chat_mute2);


    }

    private void initVar() {
        Intent intent = getIntent();
        chatType = intent.getIntExtra(CHAT_TYPE, VOIP_CALL);
        if (chatType == VOIP_INCOMING) {
            //来电话界面
            lookupIncomingCall();
            showIncomingView(mCall != null && mCall.getRemoteParams() != null && LinphoneManager.getLc().getVideoAutoAcceptPolicy() && mCall.getRemoteParams().getVideoEnabled());

        } else if (chatType == VOIP_CALL) {
            //正在通话中
            lookupCalling();
            showCallView(isVideoEnabled(mCall));
            if (mCall != null) {
                registerCallDurationTimer(null, mCall);
            }


        }

    }


    private void lookupIncomingCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            for (LinphoneCall call : calls) {
                if (LinphoneCall.State.IncomingReceived == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void lookupCalling() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            for (LinphoneCall call : calls) {
                LinphoneCall.State state = call.getState();
                if (LinphoneCall.State.OutgoingInit == state ||
                        LinphoneCall.State.OutgoingProgress == state ||
                        LinphoneCall.State.OutgoingRinging == state ||
                        LinphoneCall.State.OutgoingEarlyMedia == state ||
                        LinphoneCall.State.StreamsRunning == state ||
                        LinphoneCall.State.Paused == state ||
                        LinphoneCall.State.PausedByRemote == state ||
                        LinphoneCall.State.Pausing == state ||
                        LinphoneCall.State.Connected == state) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void initListener() {
        //
        voip_accept.setComClickListener(this);
        voip_hang_up.setComClickListener(this);
        //
        voip_chat_mute.setComClickListener(this);
        voip_chat_cancel.setComClickListener(this);
        voip_chat_hands_free.setComClickListener(this);
        //
        voip_chat_switch_voice.setComClickListener(this);
        voip_chat_cancel_video.setComClickListener(this);
        voip_chat_switch_camera.setComClickListener(this);
        voip_chat_mute2.setComClickListener(this);
        //
        voip_cancel.setComClickListener(this);
        voip_chat_switch_audio.setComClickListener(this);

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (mCall == null) {
                    mCall = call;
                }
                if (call != mCall) {
                    return;
                }
                //来电话
                if (call.getDirection() == CallDirection.Incoming) {
                    if (call == mCall && LinphoneCall.State.CallEnd == state) {
                        //对方挂断电话
                        finish();
                    }
                    //建立通话
                    if (state == LinphoneCall.State.StreamsRunning) {
                        chatType = VOIP_CALL;
                        if (isVideoEnabled(call)) {
                            voip_chat_incoming.setVisibility(View.INVISIBLE);
                            voip_video_chatting.setVisibility(View.VISIBLE);
                            switchVideo();
                        } else {
                            LinphoneManager.getLc().enableSpeaker(false);
                            // 开启计时
                            audioCallFragment.updateChatStateTips("");
                            audioCallFragment.setNarrowVisible(true);
                        }
                        registerCallDurationTimer(null, call);
                    }
                    if (state == LinphoneCall.State.CallUpdatedByRemote) {
                        boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
                        if (!remoteVideo && !LinphoneManager.getLc().isInConference()) {
                            voip_video_chatting.setVisibility(View.INVISIBLE);
                            voip_voice_chatting.setVisibility(View.VISIBLE);
                            replaceFragmentVideoByAudio();
                        }
                    }
                    if (state == LinphoneCall.State.CallEarlyUpdatedByRemote) {
                        // 切换到语音接收界面
                        replaceFragmentVideoByAudio();

                    }
                    if (state == LinphoneCall.State.CallEarlyUpdating) {
                        replaceFragmentVideoByAudio();
                    }

                    if (state == LinphoneCall.State.CallUpdating) {
                        voip_video_chatting.setVisibility(View.INVISIBLE);
                        voip_voice_chatting.setVisibility(View.VISIBLE);
                        replaceFragmentVideoByAudio();
                    }
                    if (state == LinphoneCall.State.Error) {
                        terminateErrorCall(call, message);
                    }
                } else {
                    //去电话
                    if (state == LinphoneCall.State.CallEnd) {
                        if (call.getErrorInfo().getReason() == Reason.Declined) {
                            declineOutgoing();
                        }
                    } else if (state == LinphoneCall.State.Error) {
                        terminateErrorCall(call, message);
                        declineOutgoing();
                    } else if (state == LinphoneCall.State.CallUpdatedByRemote) {
                        boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
                        if (!remoteVideo && !LinphoneManager.getLc().isInConference()) {
                            voip_video_chatting.setVisibility(View.INVISIBLE);
                            voip_voice_chatting.setVisibility(View.VISIBLE);
                            replaceFragmentVideoByAudio();
                        }
                    } else if (state == LinphoneCall.State.CallUpdating) {
                        voip_video_chatting.setVisibility(View.INVISIBLE);
                        voip_voice_chatting.setVisibility(View.VISIBLE);
                        replaceFragmentVideoByAudio();
                    }
                    if (LinphoneManager.getLc().getCallsNb() == 0) {
                        finish();
                    }
                }
            }
        };
    }

    private void terminateErrorCall(LinphoneCall call, String message) {
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        if (message != null && call.getErrorInfo().getReason() == Reason.NotFound) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Busy) {
            displayCustomToast(getString(R.string.voice_chat_busy_toast), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.BadCredentials) {
            displayCustomToast(getString(R.string.voice_chat_setmastkey_err_toast), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Declined) {
            displayCustomToast(getString(R.string.voice_chat_friend_refused_toast), Toast.LENGTH_SHORT);
        } else if (call.getErrorInfo().getReason() == Reason.Media) {
            displayCustomToast("不兼容的媒体参数", Toast.LENGTH_SHORT);
        } else if (call.getErrorInfo().getReason() == Reason.NotAnswered) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        } else if (message != null) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        }
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.voip_accept) {
            //接听电话
            answer();

        } else if (id == R.id.voip_hang_up) {
            //挂断电话
            declineIncoming();

        } else if (id == R.id.voip_chat_mute || id == R.id.voip_chat_mute2) {
            // 开启静音
            toggleMicro();

        } else if (id == R.id.voip_chat_cancel || id == R.id.voip_chat_cancel_video || id == R.id.voip_cancel) {
            if (mCall != null) {
                hangUp();
            } else {
                voipHandler.removeMessages(CALL);
                finish();
            }
        } else if (id == R.id.voip_chat_hands_free) {
            //开启扬声器
            toggleSpeaker();

        } else if (id == R.id.voip_chat_switch_voice || id == R.id.voip_chat_switch_audio) {
            // 切换到语音聊天
            disableVideo(true);

        } else if (id == R.id.voip_chat_switch_camera) {
            // 切换摄像头
            if (videoCallFragment != null) {
                videoCallFragment.switchCamera();
            }
        }

    }

    private void disableVideo(final boolean videoDisabled) {
        final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }
        if (videoDisabled) {
            LinphoneCallParams params = LinphoneManager.getLc().createCallParams(call);
            params.setVideoEnabled(false);
            LinphoneManager.getLc().updateCall(call, params);
        }
    }

    private boolean alreadyAcceptedOrDeniedCall;

    private void answer() {
        if (mCall != null) {
            if (alreadyAcceptedOrDeniedCall) {
                return;
            }
            alreadyAcceptedOrDeniedCall = true;
            LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);
            boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(VoipService.instance().getApplicationContext());
            if (params != null) {
                params.enableLowBandwidth(isLowBandwidthConnection);
            } else {
                org.linphone.mediastream.Log.e("Could not create call params for call");
            }

            if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params, null)) {
                Toast.makeText(this, getString(R.string.voice_chat_err), Toast.LENGTH_LONG).show();
            } else {
                if (isVideoEnabled(mCall)) {
                    voip_chat_incoming.setVisibility(View.INVISIBLE);
                    replaceFragmentAudioByVideo();
                } else {
                    //成功接听
                    voip_chat_incoming.setVisibility(View.INVISIBLE);
                    voip_voice_chatting.setVisibility(View.VISIBLE);
                    voip_chat_hands_free.setImageResource(R.drawable.voip_btn_voice_hand_free);
                    isSpeakerEnabled = false;
                }

            }
        }

    }

    private void declineIncoming() {
        if (mCall != null) {
            if (alreadyAcceptedOrDeniedCall) {
                return;
            }
            alreadyAcceptedOrDeniedCall = true;

            LinphoneManager.getLc().terminateCall(mCall);
            finish();
        } else {
            finish();
        }

    }

    private void declineOutgoing() {
        if (mCall == null) {
            hangUp();
            return;
        }
        LinphoneManager.getLc().terminateCall(mCall);
        finish();

    }

    private void hangUp() {
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }


    protected void toggleSpeaker() {
        isSpeakerEnabled = !isSpeakerEnabled;
        voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.voip_hands_free : R.drawable.voip_btn_voice_hand_free);
        if (isSpeakerEnabled) {
            // 打开扬声器
            LinphoneManager.getInstance().routeAudioToSpeaker();
        } else {
            //关闭扬声器
            LinphoneManager.getInstance().routeAudioToReceiver();
        }


    }

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        if (isMicMuted) {
            lc.muteMic(true);
        } else {
            lc.muteMic(false);
        }

        voip_chat_mute2.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);
        voip_chat_mute.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //屏蔽返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.voip_toast, (ViewGroup) findViewById(R.id.toastRoot));
        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);
        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }


    private boolean isVideoEnabled(LinphoneCall call) {
        if (call != null) {
            return call.getCurrentParams().getVideoEnabled();
        }
        return false;
    }


    public void bindVideoFragment(CallVideoFragment fragment) {
        videoCallFragment = fragment;
    }


    public void bindAudioFragment(CallAudioFragment fragment) {
        audioCallFragment = fragment;
    }

    private void switchVideo() {
        final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }
        LinphoneManager.getInstance().routeAudioToSpeaker();
        isSpeakerEnabled = true;
        //Check if the call is not terminated
        if (call.getState() == LinphoneCall.State.CallEnd || call.getState() == LinphoneCall.State.CallReleased)
            return;
        if (!call.getRemoteParams().isLowBandwidthEnabled()) {
            LinphoneManager.getInstance().addVideo();
            if (videoCallFragment == null || !videoCallFragment.isVisible())
                replaceFragmentAudioByVideo();
        } else {
            displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
        }

    }

    private void showIncomingView(boolean isVideo) {
        voip_voice_chatting.setVisibility(View.INVISIBLE);
        voip_video_chatting.setVisibility(View.INVISIBLE);
        voip_chat_incoming.setVisibility(View.VISIBLE);
        voip_chat_outgoing.setVisibility(View.INVISIBLE);
        if (isVideo) {
            // 检查相机权限
            if (Build.VERSION.SDK_INT >= 23) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 1000);
                }
            }
            videoPreViewFragment = new VideoPreViewFragment();
            addFragment(videoPreViewFragment, chatType);

        } else {
            audioCallFragment = new CallAudioFragment();
            addFragment(audioCallFragment, chatType);
        }


    }

    private void showCallView(boolean isVideo) {
        voip_chat_incoming.setVisibility(View.INVISIBLE);
        voip_chat_outgoing.setVisibility(View.INVISIBLE);
        if (isVideo) {
            voip_video_chatting.setVisibility(View.VISIBLE);
            voip_voice_chatting.setVisibility(View.INVISIBLE);
            videoCallFragment = new CallVideoFragment();
            addFragment(videoCallFragment, chatType);
            LinphoneManager.getInstance().routeAudioToSpeaker();
            isSpeakerEnabled = true;
        } else {
            voip_voice_chatting.setVisibility(View.VISIBLE);
            voip_video_chatting.setVisibility(View.INVISIBLE);
            audioCallFragment = new CallAudioFragment();
            addFragment(audioCallFragment, chatType);
        }
    }

    private void replaceFragmentAudioByVideo() {
        videoCallFragment = new CallVideoFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, videoCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceFragmentVideoByAudio() {
        audioCallFragment = new CallAudioFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(CHAT_TYPE, chatType);
        audioCallFragment.setArguments(bundle);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, audioCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void addFragment(Fragment fragment, int type) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Bundle bundle = new Bundle();
        bundle.putInt(CHAT_TYPE, type);
        fragment.setArguments(bundle);
        transaction.add(R.id.fragmentContainer, fragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void displayVideoCallControlsIfHidden() {
        if (voip_video_chatting.getVisibility() == View.VISIBLE) {
            voip_video_chatting.setVisibility(View.GONE);
        } else {
            voip_video_chatting.setVisibility(View.VISIBLE);
        }

    }


    // home键的监听
    public class HomeWatcherReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (TextUtils.equals(intentAction, Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                openNarrow();
            }
        }

    }

    //开启悬浮窗
    public synchronized void openNarrow() {
        SettingsCompat.setDrawOverlays(this.getApplicationContext(), true);
        VoipService.instance().createNarrowView();
        VoipActivity.this.finish();
    }

    public void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) findViewById(R.id.voip_voice_chat_time);
        } else {
            timer = (Chronometer) v;
        }
        timer.setVisibility(View.VISIBLE);
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();

    }


}