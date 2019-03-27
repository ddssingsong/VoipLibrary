package com.dds.voip;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.frgment.AudioPreViewFragment;
import com.dds.voip.frgment.VideoPreViewFragment;
import com.dds.voip.utils.StatusBarCompat;

import java.util.Timer;
import java.util.TimerTask;

import static com.dds.voip.VoipActivity.CHAT_TYPE;
import static com.dds.voip.VoipActivity.VOIP_INCOMING;

public class IncomingActivity extends AppCompatActivity implements ComButton.onComClick {

    private ComButton voip_hang_up;
    private ComButton voip_accept;


    private AudioPreViewFragment audioPreViewFragment;
    private VideoPreViewFragment videoPreViewFragment;


    private boolean isVideoEnable;
    private long groupId;
    private String callId;

    public static void openActivity(Context context, boolean isVideoEnable, long groupId, String callId) {
        if (VoipService.isReady()) {
            Intent intent = new Intent(context, IncomingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("isVideo", isVideoEnable);
            intent.putExtra("groupId", groupId);
            intent.putExtra("callId", callId);
            context.startActivity(intent);
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
        setContentView(R.layout.activity_incoming);
        initView();
        initVar();
        initListener();
    }


    private void initView() {
        voip_hang_up = findViewById(R.id.voip_hang_up);
        voip_accept = findViewById(R.id.voip_accept);

    }

    private void initVar() {
        Intent intent = getIntent();
        isVideoEnable = intent.getBooleanExtra("isVideo", false);
        groupId = intent.getLongExtra("groupId", 0);
        callId = intent.getStringExtra("callId");
        if (groupId > 0) {
            // 群会议
            startTimer();
            if (LinphoneManager.isInstanciated()) {
                LinphoneManager.getInstance().startRinging();
            }
            if (isVideoEnable) {
                videoPreViewFragment = new VideoPreViewFragment();
                addFragment(videoPreViewFragment, VOIP_INCOMING);
            } else {
                audioPreViewFragment = new AudioPreViewFragment();
                addFragment(audioPreViewFragment, VOIP_INCOMING);
            }


        }


    }

    private void initListener() {
        voip_hang_up.setComClickListener(this);
        voip_accept.setComClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.voip_accept) {
            //接听电话
            answer();

        } else if (id == R.id.voip_hang_up) {
            VoipService.stopCall(this);
            //挂断电话
            finish();

        }

    }

    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (LinphoneManager.isInstanciated()) {
            LinphoneManager.getInstance().stopRinging();
        }
        super.onPause();
    }

    private void answer() {
        VoipHelper.getInstance().call(this, callId, isVideoEnable, groupId);
        finish();
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

    private Timer timer;
    private int current = 0;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            IncomingActivity.this.finish();
        }
    };

    private void startTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                current++;
                if (current == 40) {
                    handler.sendEmptyMessage(110);
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

    }

}
