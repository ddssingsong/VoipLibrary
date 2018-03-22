package com.trustmobi.voip;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.trustmobi.voip.callback.DisplayCallback;
import com.trustmobi.voip.utils.LinphoneUtils;
import com.trustmobi.voip.voipaar.R;
import com.trustmobi.voip.widget.ComButton;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.Reason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatActivity extends Activity implements ComButton.onComClick, View.OnClickListener {

    private RelativeLayout voip_rl_audio;

    private LinearLayout voip_chat_incoming;
    private LinearLayout voip_voice_chatting;

    private ComButton voip_chat_mute;
    private ComButton voip_chat_cancel;
    private ComButton voip_chat_hands_free;

    private ComButton voip_accept;
    private ComButton voip_hang_up;

    private Button narrow_button;
    private TextView voip_voice_chat_state_tips;

    private ImageView voip_voice_chat_avatar;
    private TextView voice_chat_friend_name;

    private LinphoneCall mCall;
    private LinphoneCoreListenerBase mListener;

    HomeWatcherReceiver homeWatcherReceiver;


    private int chatType;

    // 0 播出电话 1 接听电话  2 通话中
    public static void openActivity(Context context, int chatType) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("chatType", chatType);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voip_activity_chat);
        initView();
        initVar();
        initListener();
        initReceiver();
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
    protected void onResume() {
        super.onResume();
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            isSpeakerEnabled = lc.isSpeakerEnabled();
            Log.e("dds_test", "onResume isSpeakerEnabled:" + isSpeakerEnabled);
            voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.hands_free : R.drawable.btn_voice_hand_free);
            isMicMuted = lc.isMicMuted();
            voip_chat_mute.setImageResource(isMicMuted ? R.drawable.mute : R.drawable.btn_voice_mute);
            Log.e("dds_test", "onResume isMicMuted:" + isMicMuted);
        }


    }

    @Override
    protected void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (homeWatcherReceiver != null) {
            unregisterReceiver(homeWatcherReceiver);
        }
        super.onDestroy();
    }

    private void initView() {
        voip_rl_audio = findViewById(R.id.voip_rl_audio);
        voip_chat_incoming = findViewById(R.id.voip_chat_incoming);
        voip_voice_chatting = findViewById(R.id.voip_voice_chatting);
        voip_chat_mute = findViewById(R.id.voip_chat_mute);
        voip_chat_cancel = findViewById(R.id.voip_chat_cancel);
        voip_chat_hands_free = findViewById(R.id.voip_chat_hands_free);
        voip_accept = findViewById(R.id.voip_accept);
        voip_hang_up = findViewById(R.id.voip_hang_up);
        voip_voice_chat_state_tips = findViewById(R.id.voip_voice_chat_state_tips);
        voip_voice_chat_avatar = findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = findViewById(R.id.voice_chat_friend_name);
        narrow_button = findViewById(R.id.narrow_button);

    }

    private void initVar() {
        Intent intent = getIntent();
        chatType = intent.getIntExtra("chatType", 0);
        if (chatType == 0) {
            //播出电话
            voip_chat_incoming.setVisibility(View.INVISIBLE);
            voip_voice_chatting.setVisibility(View.VISIBLE);
            voip_chat_mute.setEnable(false);
            lookupOutgoingCall();
            updateChatStateTips("正在呼叫中...");
        } else if (chatType == 1) {
            //来电话界面
            voip_chat_incoming.setVisibility(View.VISIBLE);
            voip_voice_chatting.setVisibility(View.INVISIBLE);
            lookupIncomingCall();
            updateChatStateTips("邀请您进行语音通话...");
            if (mCall != null) {
                LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                if (lc != null) {
                    lc.enableSpeaker(true);
                }
            }
        } else {
            //正在通话中
            voip_chat_incoming.setVisibility(View.INVISIBLE);
            voip_voice_chatting.setVisibility(View.VISIBLE);
            voip_chat_mute.setEnable(true);
            voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
            lookupCalling();
            //显示时间
            if (mCall != null) {
                registerCallDurationTimer(null, mCall);
            }
        }

        //来的是视频通话
        if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            voip_rl_audio.setVisibility(View.GONE);
            videoCallFragment = new VideoFragment();
            getFragmentManager().beginTransaction().add(R.id.voip_fl_video, videoCallFragment).commitAllowingStateLoss();
        }
        //显示头像和昵称
        DisplayCallback callback = VoipHelper.getInstance().getDisplayCallback();
        if (callback != null) {
            Glide.with(this).load(callback.getDisplayInfo().getAvatar()).into(voip_voice_chat_avatar);
            voice_chat_friend_name.setText(callback.getDisplayInfo().getNickName());
        }
    }

    private void lookupOutgoingCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            for (LinphoneCall call : calls) {
                LinphoneCall.State cstate = call.getState();
                if (LinphoneCall.State.OutgoingInit == cstate || LinphoneCall.State.OutgoingProgress == cstate
                        || LinphoneCall.State.OutgoingRinging == cstate || LinphoneCall.State.OutgoingEarlyMedia == cstate) {
                    mCall = call;
                    break;
                }
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
                if (LinphoneCall.State.StreamsRunning == call.getState() ||
                        LinphoneCall.State.Paused == call.getState() ||
                        LinphoneCall.State.PausedByRemote == call.getState() ||
                        LinphoneCall.State.Pausing == call.getState() ||
                        LinphoneCall.State.Connected == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void initListener() {
        voip_chat_mute.setComClickListener(this);
        voip_chat_cancel.setComClickListener(this);
        voip_chat_hands_free.setComClickListener(this);
        voip_accept.setComClickListener(this);
        voip_hang_up.setComClickListener(this);
        narrow_button.setOnClickListener(this);
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                //来电话
                if (call.getDirection() == CallDirection.Incoming) {
                    if (call == mCall && LinphoneCall.State.CallEnd == state) {
                        //对方挂断电话
                        finish();
                    }
                    //建立通话
                    if (state == LinphoneCall.State.StreamsRunning) {
                        LinphoneManager.getLc().enableSpeaker(false);
                        registerCallDurationTimer(null, call);
                        voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
                    }
                } else {
                    //去电话
                    if (call == mCall && LinphoneCall.State.OutgoingRinging == state) {
                        updateChatStateTips("正在等待对方接受邀请...");
                    } else if (call == mCall && LinphoneCall.State.Connected == state) {
                        //开始接听
                        updateChatStateTips("连接中...");
                        voip_chat_mute.setEnable(true);
                        return;
                    } else if (call == mCall && LinphoneCall.State.StreamsRunning == state) {
                        registerCallDurationTimer(null, call);
                        voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);

                    } else if (state == LinphoneCall.State.CallEnd) {
                        if (call.getErrorInfo().getReason() == Reason.Declined) {
                            displayCustomToast("拒接来电", Toast.LENGTH_SHORT);
                            declineOutgoing();
                        }
                    } else if (state == LinphoneCall.State.Error) {
                        // Convert LinphoneCore message for internalization
                        if (call.getErrorInfo().getReason() == Reason.Declined) {
                            displayCustomToast("拒接来电", Toast.LENGTH_SHORT);
                            declineOutgoing();
                        } else if (call.getErrorInfo().getReason() == Reason.NotFound) {
                            displayCustomToast("未找到用户", Toast.LENGTH_SHORT);
                            declineOutgoing();
                        } else if (call.getErrorInfo().getReason() == Reason.Media) {
                            displayCustomToast("不兼容的媒体参数", Toast.LENGTH_SHORT);
                            declineOutgoing();
                        } else if (call.getErrorInfo().getReason() == Reason.Busy) {
                            displayCustomToast("用户正忙", Toast.LENGTH_SHORT);
                            declineOutgoing();
                        } else if (message != null) {
                            displayCustomToast("未知错误 - " + message, Toast.LENGTH_SHORT);
                            declineOutgoing();
                        }
                    }

                    if (LinphoneManager.getLc().getCallsNb() == 0) {
                        finish();
                    }
                }


            }
        };
    }

    private boolean isSpeakerEnabled = false, isMicMuted = false;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.voip_accept) {
            //接听电话
            answer();

        } else if (id == R.id.voip_hang_up) {
            //挂断电话
            declineIncoming();

        } else if (id == R.id.voip_chat_mute) {
            toggleMicro();

        } else if (id == R.id.voip_chat_cancel) {
            if (mCall.getState() == LinphoneCall.State.StreamsRunning ||
                    mCall.getState() == LinphoneCall.State.Paused ||
                    mCall.getState() == LinphoneCall.State.PausedByRemote ||
                    mCall.getState() == LinphoneCall.State.Connected
                    ) {
                hangUp();

            } else {
                declineOutgoing();
            }

        } else if (id == R.id.voip_chat_hands_free) {
            toggleSpeaker();

        } else if (id == R.id.narrow_button) {
            openNarrow();
        }

    }

    private boolean alreadyAcceptedOrDeniedCall, begin;

    private void answer() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;
        LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);
        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());
        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
        } else {
            org.linphone.mediastream.Log.e("Could not create call params for call");
        }

        if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
            Toast.makeText(this, "来电接通出错", Toast.LENGTH_LONG).show();
        } else {
            //接通电话
            voip_chat_incoming.setVisibility(View.INVISIBLE);
            voip_voice_chatting.setVisibility(View.VISIBLE);
        }
    }

    private void declineIncoming() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;

        LinphoneManager.getLc().terminateCall(mCall);
        finish();
    }

    private void declineOutgoing() {
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
        voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.hands_free : R.drawable.btn_voice_hand_free);
        LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);

    }

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        if (isMicMuted) {
            lc.muteMic(true);
            isSpeakerEnabled = false;
            lc.enableSpeaker(false);
            voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.hands_free : R.drawable.btn_voice_hand_free);
        } else {
            lc.muteMic(false);
        }


        voip_chat_mute.setImageResource(isMicMuted ? R.drawable.mute : R.drawable.btn_voice_mute);

    }


    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) findViewById(R.id.voip_voice_chat_time);
        }
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    private void updateChatStateTips(String tips) {
        voip_voice_chat_state_tips.setVisibility(View.VISIBLE);
        voip_voice_chat_state_tips.setText(tips);
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
        TextView toastText = layout.findViewById(R.id.toastMessage);
        toastText.setText(message);
        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public class HomeWatcherReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (TextUtils.equals(intentAction, Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                openNarrow();
            }
        }

    }


    private void openNarrow() {
        SettingsCompat.setDrawOverlays(this, true);
        LinphoneService.instance().createNarrowView();
        ChatActivity.this.finish();
    }

    private VideoFragment videoCallFragment;

    public void bindVideoFragment(VideoFragment fragment) {
        videoCallFragment = fragment;
    }

    private boolean isVideoEnabled(LinphoneCall call) {
        if (call != null) {
            return call.getCurrentParams().getVideoEnabled();
        }
        return false;
    }

}
