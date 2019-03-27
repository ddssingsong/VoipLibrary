package com.dds.voip.frgment;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.dds.tbs.linphonesdk.R;
import com.dds.voip.RoundedCornersTransformation;
import com.dds.voip.VoipHelper;
import com.dds.voip.VoipService;
import com.dds.voip.bean.ChatInfo;
import com.dds.voip.callback.VoipCallBack;

/**
 * Created by dds on 2018/8/3.
 * android_shuai@163.com
 */

public class AudioPreViewFragment extends Fragment {

    private ImageView iv_background;
    private ImageView voip_voice_chat_avatar;
    private TextView voice_chat_friend_name;
    private TextView voip_voice_chat_state_tips;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio_preview, container, false);
        initView(view);
        initVar();
        return view;
    }

    private void initView(View rootView) {
        voip_voice_chat_avatar = rootView.findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = rootView.findViewById(R.id.voice_chat_friend_name);
        iv_background = rootView.findViewById(R.id.iv_background);
        voip_voice_chat_state_tips = rootView.findViewById(R.id.voip_voice_chat_state_tips);
    }

    private ChatInfo info;

    private void initVar() {
        //显示头像和昵称
        info = VoipHelper.getInstance().getChatInfo();
        if (VoipHelper.mGroupId != 0) {
            VoipCallBack callBack = VoipService.instance.getCallBack();
            if (callBack != null) {
                info = callBack.getGroupInFo(VoipHelper.mGroupId);
            }
        } else {
            if (!TextUtils.isEmpty(VoipHelper.friendName)) {
                VoipCallBack callBack = VoipService.instance.getCallBack();
                if (callBack != null) {
                    info = callBack.getChatInfo(VoipHelper.friendName);
                }
            }

        }
        if (info != null) {
            Glide.with(this)
                    .load(info.getRemoteAvatar())
                    .transform(new RoundedCornersTransformation(getActivity(), 4))
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

    //更新提示语
    public void updateChatStateTips(String tips) {
        voip_voice_chat_state_tips.setVisibility(View.VISIBLE);
        voip_voice_chat_state_tips.setText(tips);
    }

}
