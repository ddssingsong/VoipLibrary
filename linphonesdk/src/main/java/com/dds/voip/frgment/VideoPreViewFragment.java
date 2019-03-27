package com.dds.voip.frgment;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

import static com.dds.voip.VoipActivity.CHAT_TYPE;
import static com.dds.voip.VoipActivity.VOIP_CALL;
import static com.dds.voip.VoipActivity.VOIP_INCOMING;
import static com.dds.voip.VoipActivity.VOIP_OUTGOING;


/**
 * Created by dds on 2018/7/19.
 * android_shuai@163.com
 * <p>
 * 视频电话预览界面
 */

public class VideoPreViewFragment extends Fragment implements SurfaceHolder.Callback {
    private SurfaceView mCaptureView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;//摄像头
    private ImageView voip_voice_chat_avatar;
    private TextView voice_chat_friend_name;
    private TextView voip_voice_chat_state_tips;

    private int chatType;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        chatType = bundle.getInt(CHAT_TYPE, VOIP_CALL);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.voip_video_preview, container, false);
        mCaptureView = (SurfaceView) view.findViewById(R.id.videoCaptureSurface);
        voip_voice_chat_avatar = (ImageView) view.findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = (TextView) view.findViewById(R.id.voice_chat_friend_name);
        voip_voice_chat_state_tips = (TextView) view.findViewById(R.id.voip_voice_chat_state_tips);
        surfaceHolder = mCaptureView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        surfaceHolder.setKeepScreenOn(true);
        surfaceHolder.addCallback(this);
        initVar();
        if (chatType == VOIP_INCOMING) {
            updateChatStateTips(getString(R.string.voice_chat_invite));
        } else if (chatType == VOIP_OUTGOING) {
            updateChatStateTips(getString(R.string.voice_chat_calling));
        }

        return view;
    }

    //更新提示语
    public void updateChatStateTips(String tips) {
        voip_voice_chat_state_tips.setVisibility(View.VISIBLE);
        voip_voice_chat_state_tips.setText(tips);
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
            voice_chat_friend_name.setText(info.getRemoteNickName());
        }
    }

    private void initCamera() {
        if (camera != null) {
            camera.startPreview();
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            int cameraCount = Camera.getNumberOfCameras();
            for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    camera = Camera.open(camIdx);
                    setCameraDisplayOrientation(getActivity(), camIdx, camera);

                }
            }
            if (camera == null) {
                camera = Camera.open();
                setCameraDisplayOrientation(getActivity(), 0, camera);
            }
            camera.setPreviewDisplay(surfaceHolder);
            initCamera();
        } catch (Exception e) {
            if (null != camera) {
                camera.release();
                camera = null;
            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initCamera();

    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != camera) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void onPause() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {

        Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        //获取摄像头信息
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        //获取摄像头当前的角度
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            //前置摄像头
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else {
            // back-facing  后置摄像头
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }
}
