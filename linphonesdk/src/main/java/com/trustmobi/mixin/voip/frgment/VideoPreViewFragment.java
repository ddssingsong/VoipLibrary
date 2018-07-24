package com.trustmobi.mixin.voip.frgment;

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
import com.trustmobi.mixin.voip.RoundedCornersTransformation;
import com.trustmobi.mixin.voip.VoipHelper;
import com.trustmobi.mixin.voip.VoipService;
import com.trustmobi.mixin.voip.bean.ChatInfo;
import com.trustmobi.mixin.voip.callback.VoipCallBack;


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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.voip_video_preview, container, false);
        mCaptureView = (SurfaceView) view.findViewById(R.id.videoCaptureSurface);
        voip_voice_chat_avatar = (ImageView) view.findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = (TextView) view.findViewById(R.id.voice_chat_friend_name);
        surfaceHolder = mCaptureView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        surfaceHolder.setKeepScreenOn(true);
        surfaceHolder.addCallback(this);
        initVar();
        return view;
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
            voice_chat_friend_name.setText(info.getRemoteNickName());
        }
    }

    private void initCamera() {
        camera.startPreview();
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
    public void onDestroyView() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
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
