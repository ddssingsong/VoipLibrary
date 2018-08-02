package com.trustmobi.mixin.voip.frgment;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dds.tbs.linphonesdk.R;

/**
 * Created by dds on 2018/7/31.
 * android_shuai@163.com
 */

public class MeetingPreViewFragment extends Fragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.voip_meeting_preview, container, false);
        initView(view);
        return view;
    }

    private void initView(View view) {

    }
}
