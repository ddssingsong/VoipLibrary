package com.trustmobi.mixin.voip.frgment;

/*
CallAudioFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Fragment;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.dds.tbs.linphonesdk.R;
import com.trustmobi.mixin.voip.LinphoneManager;
import com.trustmobi.mixin.voip.RoundedCornersTransformation;
import com.trustmobi.mixin.voip.VoipActivity;
import com.trustmobi.mixin.voip.VoipHelper;
import com.trustmobi.mixin.voip.VoipService;
import com.trustmobi.mixin.voip.bean.ChatInfo;
import com.trustmobi.mixin.voip.callback.VoipCallBack;

import org.linphone.core.LinphoneCall;

import static com.trustmobi.mixin.voip.VoipActivity.CHAT_TYPE;
import static com.trustmobi.mixin.voip.VoipActivity.VOIP_CALL;
import static com.trustmobi.mixin.voip.VoipActivity.VOIP_INCOMING;
import static com.trustmobi.mixin.voip.VoipActivity.VOIP_OUTGOING;

public class CallAudioFragment extends Fragment implements View.OnClickListener {

    private Button narrow_button;

    private ImageView iv_background;
    private ImageView voip_voice_chat_avatar;
    private TextView voice_chat_friend_name;
    private TextView voip_voice_chat_state_tips;
    private Chronometer voip_voice_chat_time;


    private VoipActivity incallActvityInstance;
    private View rootView = null;

    private int chatType;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        chatType = bundle.getInt(CHAT_TYPE, VOIP_CALL);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.voip_audio, container, false);
            initView(rootView);
            initListener();
            initVar();
        }

        return rootView;
    }

    private void initView(View rootView) {
        narrow_button = (Button) rootView.findViewById(R.id.narrow_button);
        voip_voice_chat_state_tips = (TextView) rootView.findViewById(R.id.voip_voice_chat_state_tips);
        voip_voice_chat_avatar = (ImageView) rootView.findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = (TextView) rootView.findViewById(R.id.voice_chat_friend_name);
        iv_background = (ImageView) rootView.findViewById(R.id.iv_background);
        voip_voice_chat_time = rootView.findViewById(R.id.voip_voice_chat_time);

        if (chatType == VOIP_INCOMING) {
            updateChatStateTips(getString(R.string.voice_chat_invite));
        } else if (chatType == VOIP_OUTGOING) {
            updateChatStateTips(getString(R.string.voice_chat_calling));
        } else {
            voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
            LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
            registerCallDurationTimer(voip_voice_chat_time,call);
        }
    }

    private void initListener() {
        narrow_button.setOnClickListener(this);

    }

    private ChatInfo info;

    private void initVar() {
        //显示头像和昵称
        info = VoipHelper.getInstance().getChatInfo();
        if (!TextUtils.isEmpty(VoipHelper.friendName)) {
            VoipCallBack callBack = VoipService.instance.getCallBack();
            if (callBack != null) {
                info = callBack.getChatInfo(VoipHelper.friendName);
            }
        }
        if (info != null) {
            Glide.with(this)
                    .load(info.getRemoteAvatar())
                    .transform(new RoundedCornersTransformation(getActivity(), 10))
                    .placeholder(info.getDefaultAvatar())
                    .error(info.getDefaultAvatar())
                    .into(voip_voice_chat_avatar);
            Glide.with(this)
                    .load(info.getRemoteAvatar())
                    .placeholder(info.getDefaultAvatar())
                    .error(info.getDefaultAvatar())
                    .into(iv_background);
            voice_chat_friend_name.setText(info.getRemoteNickName());
        }

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.narrow_button) {
            // 开启悬浮窗
            incallActvityInstance.openNarrow();
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        incallActvityInstance = (VoipActivity) getActivity();
        if (incallActvityInstance != null) {
            incallActvityInstance.bindAudioFragment(this);
        }
        // Just to be sure we have incall controls
        if (incallActvityInstance != null) {
            //incallActvityInstance.removeCallbacks();
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rootView != null) {
            ViewGroup viewGroup = (ViewGroup) rootView.getParent();
            if (viewGroup != null) {
                viewGroup.removeView(rootView);
            }
        }
    }

    @Override
    public void onDestroy() {
        rootView = null;
        super.onDestroy();

    }


    //更新提示语
    public void updateChatStateTips(String tips) {
        voip_voice_chat_state_tips.setVisibility(View.VISIBLE);
        voip_voice_chat_state_tips.setText(tips);
    }

    public void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) rootView.findViewById(R.id.voip_voice_chat_time);
            timer.setVisibility(View.VISIBLE);
        }
        if (timer != null) {
            timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
            timer.start();
        }

    }


}
