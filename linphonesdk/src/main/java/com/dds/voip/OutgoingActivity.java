package com.dds.voip;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.frgment.AudioPreViewFragment;
import com.dds.voip.frgment.VideoPreViewFragment;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dds.voip.VoipActivity.CALL;
import static com.dds.voip.VoipActivity.CHAT_TYPE;
import static com.dds.voip.VoipActivity.VOIP_CALL;
import static com.dds.voip.VoipActivity.VOIP_OUTGOING;

/**
 * VoIP语音和视频的拨出界面
 */
public class OutgoingActivity extends AppCompatActivity implements ComButton.onComClick {
    private ComButton voip_cancel;
    private ComButton voip_chat_mute;
    private ComButton voip_chat_hands_free;

    private LinphoneCall mCall;
    private LinphoneCoreListenerBase mListener;
    private HomeWatcherReceiver homeWatcherReceiver;
    private VoipHandler voipHandler = new VoipHandler();

    private VideoPreViewFragment videoPreViewFragment;
    private AudioPreViewFragment audioPreViewFragment;
    public static final String IS_VIDEO = "isVideo";


    public static OutgoingActivity outgoingActivity;

    public static void openActivity(Context context, boolean isVideo) {
        Intent intent = new Intent(context, OutgoingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(IS_VIDEO, isVideo);
        context.startActivity(intent);
    }

    private boolean isVideo;
    private boolean isSpeakerEnabled = false, isMicMuted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing);
        outgoingActivity = this;
        initView();
        initVar();
        initListener();
        lookupOutgoingCall();
        if (mCall == null) {
            //延后2秒拨出电话，保证对方已经启动VoIP服务
            voipHandler.sendEmptyMessageDelayed(CALL, 2000);
        }
        initReceiver();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        initVar();
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lookupOutgoingCall();
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            isMicMuted = lc.isMicMuted();
            isSpeakerEnabled = lc.isSpeakerEnabled();
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

    private void initView() {
        voip_cancel = findViewById(R.id.voip_cancel);
        voip_chat_mute = findViewById(R.id.voip_chat_mute);
        voip_chat_hands_free = findViewById(R.id.voip_chat_hands_free);
    }

    private void initVar() {
        Intent intent = getIntent();
        isVideo = intent.getBooleanExtra(IS_VIDEO, false);
        if (isVideo) {
            videoPreViewFragment = new VideoPreViewFragment();
            addFragment(videoPreViewFragment, VOIP_OUTGOING);
            voip_chat_mute.setVisibility(View.GONE);
            voip_chat_hands_free.setVisibility(View.GONE);
        } else {
            audioPreViewFragment = new AudioPreViewFragment();
            addFragment(audioPreViewFragment, VOIP_OUTGOING);
            voip_chat_mute.setVisibility(View.VISIBLE);
            voip_chat_hands_free.setVisibility(View.VISIBLE);
        }

    }

    private void initReceiver() {
        homeWatcherReceiver = new HomeWatcherReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(homeWatcherReceiver, filter);

    }

    private void initListener() {
        voip_cancel.setComClickListener(this);
        voip_chat_mute.setComClickListener(this);
        voip_chat_hands_free.setComClickListener(this);
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (mCall == null) {
                    mCall = call;
                }
                if (call != mCall) {
                    return;
                }
                if (LinphoneCall.State.Connected == state) {
                    if (isVideoEnabled(call)) {
                        if (videoPreViewFragment != null) {
                            videoPreViewFragment.updateChatStateTips(getString(R.string.voice_chat_connect));
                        }

                    } else {
                        if (audioPreViewFragment != null) {
                            audioPreViewFragment.updateChatStateTips(getString(R.string.voice_chat_connect));
                        }
                    }

                } else if (LinphoneCall.State.StreamsRunning == state) {
                    // 对方接听
                    VoipActivity.openActivity(OutgoingActivity.this, VOIP_CALL, true);
                    OutgoingActivity.this.finish();
                } else if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error) {
                    finish();
                    VoipHelper.isInCall = false;
                } else if (state == LinphoneCall.State.CallEarlyUpdating) {
                    // 本地切换到视频或者切换到音频

                } else if (state == LinphoneCall.State.CallEarlyUpdatedByRemote) {
                    // 对方切换到视频或者音频

                }


            }
        };
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.voip_cancel) {
            // 取消去电
            hangUp();
            VoipService.stopCall(this);
            finish();
        } else if (id == R.id.voip_chat_mute) {
            //设置静音
            toggleMicro();

        } else if (id == R.id.voip_chat_hands_free) {
            //设置扬声器
            toggleSpeaker();
        }
    }

    @Override
    public void onBackPressed() {
        //点击挂断电话
        hangUp();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        outgoingActivity = null;
        if (homeWatcherReceiver != null) {
            unregisterReceiver(homeWatcherReceiver);
        }
        if (voipHandler != null) {
            voipHandler.removeMessages(CALL);
        }
        super.onDestroy();

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

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        if (isMicMuted) {
            lc.muteMic(true);
        } else {
            lc.muteMic(false);
        }
        voip_chat_mute.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);

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

    //挂断拨出电话
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
        VoipHelper.isInCall = false;
    }

    //查找拨出对话
    private void lookupOutgoingCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            LinLog.e(VoipHelper.TAG, "lookupOutgoingCall:" + calls.size());
            boolean isInCall = false;
            for (LinphoneCall call : calls) {
                LinphoneCall.State cate = call.getState();
                if (LinphoneCall.State.OutgoingInit == cate ||
                        LinphoneCall.State.OutgoingProgress == cate ||
                        LinphoneCall.State.OutgoingRinging == cate ||
                        LinphoneCall.State.OutgoingEarlyMedia == cate) {
                    mCall = call;
                    break;
                } else if (LinphoneCall.State.StreamsRunning == cate) {
                    isInCall = true;
                }
            }

            if (isInCall) {
                //跳转到通话界面
                VoipActivity.openActivity(this, VOIP_CALL);
                finish();

            }
        }
    }

    private boolean isVideoEnabled(LinphoneCall call) {
        return call != null && call.getCurrentParams().getVideoEnabled();
    }

    //延时拨打电话
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

    // home键的监听
    public class HomeWatcherReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (TextUtils.equals(intentAction, Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                //点击挂断电话
                hangUp();
                finish();
            }
        }

    }


}
