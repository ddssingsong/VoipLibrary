package com.dds.voip;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.dds.tbs.linphonesdk.R;
import com.dds.voip.callback.VoipCallBack;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneNatPolicy;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PublishState;
import org.linphone.core.Reason;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.tools.H264Helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 */

public class LinphoneManager implements LinphoneCoreListener {

    private final String mLPConfigXsd;
    private final String mLinphoneFactoryConfigFile;
    private final String mLinphoneRootCaFile;
    public final String mLinphoneConfigFile;
    private final String mRingSoundFile;
    private final String mRingbackSoundFile;
    private final String mPauseSoundFile;
    private final String mErrorToneFile;
    private final String mUserCertificatePath;
    private final String mChatDatabaseFile;
    private final String mCallLogDatabaseFile;
    private final String mFriendsDatabaseFile;


    private static LinphoneManager instance;
    private LinphoneCore mLc;
    private Context mContext;
    private Resources mR;

    private ConnectivityManager mConnectivityManager;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private MediaPlayer mRingerPlayer;
    private SensorManager mSensorManager;
    private Sensor mProximity;
    private PowerManager mPowerManager;

    private String basePath;

    public static final int NOT_ANSWER_TIME = 40;

    protected LinphoneManager(final Context c) {
        mContext = c;
        basePath = c.getFilesDir().getAbsolutePath();
        mLPConfigXsd = basePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = basePath + "/linphonerc";
        mLinphoneConfigFile = basePath + "/.linphonerc";
        mLinphoneRootCaFile = basePath + "/rootca.pem";
        mRingSoundFile = basePath + "/oldphone_mono.wav";
        mRingbackSoundFile = basePath + "/ringback.wav";
        mPauseSoundFile = basePath + "/hold.mkv";
        mErrorToneFile = basePath + "/error.wav";
        mChatDatabaseFile = basePath + "/linphone-history.db";
        mCallLogDatabaseFile = basePath + "/linphone-log-history.db";
        mFriendsDatabaseFile = basePath + "/linphone-friends.db";
        mUserCertificatePath = basePath;
        mR = c.getResources();

        mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    public static synchronized final LinphoneManager getInstance() {
        if (instance != null) return instance;
        throw new RuntimeException("Linphone Manager should be created before accessed");
    }

    public static synchronized LinphoneCore getLcIfManagerNotDestroyedOrNull() {
        if (instance == null) {
            return null;
        }
        return getLc();
    }

    public static synchronized final LinphoneCore getLc() {
        return getInstance().mLc;
    }

    public static final boolean isInstanciated() {
        return instance != null;
    }

    //初始化voip ,只初始化一次
    public synchronized static final LinphoneManager createAndStart(Context c) {
        if (instance != null)
            throw new RuntimeException("Linphone Manager is already initialized");

        instance = new LinphoneManager(c);
        instance.startLibLinphone(c);

        H264Helper.setH264Mode(H264Helper.MODE_AUTO, getLc());
        return instance;
    }

    private Timer mTimer;

    private synchronized void startLibLinphone(Context c) {
        try {
            copyAssetsFromPackage();
            mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, mLinphoneConfigFile, mLinphoneFactoryConfigFile, null, c);
            TimerTask lTask = new TimerTask() {
                @Override
                public void run() {
                    UIThreadDispatcher.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            if (mLc != null) {
                                mLc.iterate();
                            }
                        }
                    });
                }
            };
            mTimer = new Timer("Linphone scheduler");
            mTimer.schedule(lTask, 0, 50);
        } catch (Exception e) {
            LinLog.e(VoipHelper.TAG, "LinphoneManager Cannot start linphone");
        }
    }

    private void copyAssetsFromPackage() throws IOException {
        copyIfNotExist(R.raw.notes_of_the_optimistic, mRingSoundFile);
        copyIfNotExist(R.raw.ringback, mRingbackSoundFile);
        copyIfNotExist(R.raw.hold, mPauseSoundFile);
        copyIfNotExist(R.raw.incoming_chat, mErrorToneFile);
        copyIfNotExist(R.raw.linphonerc_default, mLinphoneConfigFile);
        copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
        copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
        copyFromPackage(R.raw.rootca, new File(mLinphoneRootCaFile).getName());
    }

    public void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    public void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream = mContext.openFileOutput(target, 0);
        InputStream lInputStream = mR.openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }


    //初始化一些操作
    private void initLiblinphone(LinphoneCore lc) throws LinphoneCoreException {
        mLc = lc;
        mLc.setZrtpSecretsCache(basePath + "/zrtp_secrets");
        try {
            String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
            }
            mLc.setUserAgent("Android", versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("linphone", "cannot get version name");
        }

        mLc.setRingback(mRingbackSoundFile);
        mLc.setRootCA(mLinphoneRootCaFile);
        mLc.setPlayFile(mPauseSoundFile);
        mLc.setChatDatabasePath(mChatDatabaseFile);
        mLc.setCallLogsDatabasePath(mCallLogDatabaseFile);
        mLc.setFriendsDatabasePath(mFriendsDatabaseFile);
        mLc.setUserCertificatesPath(mUserCertificatePath);
        mLc.setNetworkReachable(true);
        mLc.setVideoPolicy(true, true);
        enableDeviceRingtone(true);


        int availableCores = Runtime.getRuntime().availableProcessors();
        mLc.setCpuCount(availableCores);
        mLc.migrateCallLogs();
        //设置加密方式
        LinphoneCore.MediaEncryption menc = LinphoneCore.MediaEncryption.None;
        mLc.setMediaEncryption(menc);

        //设置如果对方多久未接进行挂断
        mLc.setIncomingTimeout(NOT_ANSWER_TIME);
        //enableJustOneAudioCodec("opus");
        enableJustOneVideoCodec("h264");


        resetCameraFromPreferences();

        callGsmON = false;
    }

    private void enableDeviceRingtone(boolean use) {
        if (use) {
            mLc.setRing(null);
        } else {
            mLc.setRing(mRingSoundFile);
        }
    }

    private void resetCameraFromPreferences() {
        boolean useFrontCam = true;
        int camId = 0;
        AndroidCameraConfiguration.AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCameraConfiguration.AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing == useFrontCam)
                camId = androidCamera.id;
        }
        LinphoneManager.getLc().setVideoDevice(camId);
    }

    //拨打电话
    public synchronized void newOutgoingCall(String to, boolean videoEnable) {
        newOutgoingCall(to, to, videoEnable);
    }

    public void newOutgoingCall(String to, String displayName, boolean videoEnable) {
        if (to == null) return;
        LinLog.e(VoipHelper.TAG, "call to number:" + to);
        LinphoneProxyConfig lpc = getLc().getDefaultProxyConfig();
        if (lpc != null) {
            to = lpc.normalizePhoneNumber(to);
        }
        LinphoneAddress lAddress;
        try {
            lAddress = mLc.interpretUrl(to);
        } catch (LinphoneCoreException e) {
            return;
        }
        lAddress.setDisplayName(displayName);
        mLc.enableSpeaker(false);
        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(mContext);
        if (mLc.isNetworkReachable()) {
            try {
                if (Version.isVideoCapable()) {
                    boolean prefVideoEnable = getLc().isVideoSupported() && getLc().isVideoEnabled();
                    CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && videoEnable, isLowBandwidthConnection, "");
                } else {
                    CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection, "");
                }
            } catch (LinphoneCoreException e) {
                e.printStackTrace();
            }
        }
    }


    private void enableJustOneAudioCodec(String codecName) {
        for (PayloadType pt : LinphoneManager.getLc().getAudioCodecs()) {
            try {
                if (pt.getMime().toLowerCase().equals(codecName)) {
                    LinphoneManager.getLc().enablePayloadType(pt, true);
                } else {
                    LinphoneManager.getLc().enablePayloadType(pt, false);
                }
            } catch (LinphoneCoreException ex) {
                ex.printStackTrace();
            }

        }
    }


    private void enableJustOneVideoCodec(String codecName) {
        for (PayloadType pt : LinphoneManager.getLc().getVideoCodecs()) {
            try {
                if (pt.getMime().toLowerCase().equals(codecName)) {
                    LinphoneManager.getLc().enablePayloadType(pt, true);
                } else {
                    LinphoneManager.getLc().enablePayloadType(pt, false);
                }
            } catch (LinphoneCoreException ex) {
                ex.printStackTrace();
            }

        }
    }

    public void abandonAudioManager() {
        if (mContext != null && mAudioManager != null && isInstanciated()) {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                org.linphone.mediastream.Log.d("---AudioManager: back to MODE_NORMAL");
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                org.linphone.mediastream.Log.d("All call terminated, routing back to earpiece");
                routeAudioToReceiver();
            }
        }

    }

    public void deleteAllAccount() {
        LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (prxCfgs != null && prxCfgs.length > 0) {
            for (int i = 0; i < prxCfgs.length; i++) {
                getLc().removeProxyConfig(prxCfgs[i]);
                LinphoneAuthInfo authInfo = getAuthInfo(i);
                if (authInfo != null) {
                    getLc().removeAuthInfo(authInfo);
                }
            }
        }
        getLc().refreshRegisters();
    }

    public LinphoneProxyConfig getProxyConfig() {
        if (mLc != null) {
            LinphoneProxyConfig[] proxyConfigList = mLc.getProxyConfigList();
            if (proxyConfigList.length > 1) {
                LinphoneProxyConfig config = mLc.getDefaultProxyConfig();
                return config;
            } else {
                LinphoneProxyConfig config = proxyConfigList[0];
                return config;
            }
        }
        return null;
    }

    private LinphoneProxyConfig getProxyConfig(int n) {
        LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (n < 0 || n >= prxCfgs.length)
            return null;
        return prxCfgs[n];
    }

    public LinphoneAuthInfo getAuthInfo() {
        if (instance == null) return null;
        LinphoneProxyConfig prxCfg = getProxyConfig();
        if (prxCfg == null) return null;
        try {
            LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxCfg.getIdentity());
            LinphoneAuthInfo authInfo = getLc().findAuthInfo(addr.getUserName(), null, addr.getDomain());
            return authInfo;
        } catch (LinphoneCoreException e) {
            org.linphone.mediastream.Log.e(e);
        }
        return null;
    }

    private LinphoneAuthInfo getAuthInfo(int n) {
        LinphoneProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) return null;
        try {
            LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxCfg.getIdentity());
            LinphoneAuthInfo authInfo = getLc().findAuthInfo(addr.getUserName(), null, addr.getDomain());
            return authInfo;
        } catch (LinphoneCoreException e) {
            org.linphone.mediastream.Log.e(e);
        }

        return null;
    }


    public void setIceEnabled(boolean enabled) {
        LinphoneNatPolicy nat = getOrCreateNatPolicy();
        nat.enableIce(enabled);
        LinphoneManager.getLc().setNatPolicy(nat);
    }


    public void setStunServer(String stun) {
        LinphoneNatPolicy nat = getOrCreateNatPolicy();
        nat.setStunServer(stun);

        if (stun != null && !stun.isEmpty()) {
            nat.enableStun(true);
        }
        LinphoneManager.getLc().setNatPolicy(nat);
    }


    // @return false if already in video call.
    public boolean addVideo() {
        LinphoneCall call = mLc.getCurrentCall();
        enableCamera(call, true);
        return reinviteWithVideo();
    }

    public void enableCamera(LinphoneCall call, boolean enable) {
        if (call != null) {
            call.enableCamera(enable);
        }
    }

    public static boolean reinviteWithVideo() {
        return CallManager.getInstance().reinviteWithVideo();
    }

    private LinphoneNatPolicy getOrCreateNatPolicy() {
        LinphoneNatPolicy nat = getLc().getNatPolicy();
        if (nat == null) {
            nat = getLc().createNatPolicy();
        }
        return nat;
    }

    public static synchronized void destroy() {
        if (instance == null) return;
        getInstance().changeStatusToOffline();
        instance.doDestroy();
    }

    private void doDestroy() {
        try {
            mTimer.cancel();
            mLc.clearProxyConfigs();
            mLc.clearAuthInfos();
            mLc.destroy();
        } catch (RuntimeException e) {
            Log.e("dds_voip", "LinphoneManager-doDestroy" + e.toString());
        } finally {
            mLc = null;
            instance = null;
        }
    }

    public void changeStatusToOffline() {
        LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
        if (isInstanciated() && lc != null) {
            lc.getPresenceModel().setBasicStatus(PresenceBasicStatus.Closed);
        }
    }


    public boolean acceptCallWithParams(LinphoneCall call, LinphoneCallParams params, String masterkey) {
        try {
            mLc.acceptCallWithParams(call, params);
            return true;
        } catch (LinphoneCoreException e) {
            Log.i("dds", "Accept call failed");
        }
        return false;
    }


    @Override
    public void authInfoRequested(LinphoneCore lc, String realm, String username, String Domain) {
        LinLog.d("dds_voip", "LinphoneManager authInfoRequested->realm:" + realm + ",username:" + username + ",domain:" + Domain);
    }

    @Override
    public void authenticationRequested(LinphoneCore lc, LinphoneAuthInfo authInfo, LinphoneCore.AuthMethod method) {

    }

    @Override
    public void callStatsUpdated(LinphoneCore lc, LinphoneCall call, LinphoneCallStats stats) {

    }

    @Override
    public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {

    }

    @Override
    public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneCall call, LinphoneAddress from, byte[] event) {

    }

    @Override
    public void transferState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State new_call_state) {

    }

    @Override
    public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev, SubscriptionState state) {

    }

    @Override
    public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev, PublishState state) {

    }

    @Override
    public void show(LinphoneCore lc) {

    }

    @Override
    public void displayStatus(LinphoneCore lc, String message) {

    }

    @Override
    public void displayMessage(LinphoneCore lc, String message) {

    }

    @Override
    public void displayWarning(LinphoneCore lc, String message) {

    }

    @Override
    public void fileTransferProgressIndication(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, int progress) {

    }

    @Override
    public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, byte[] buffer, int size) {

    }

    @Override
    public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, ByteBuffer buffer, int size) {
        return 0;
    }

    @Override
    public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {
        LinLog.d("dds_voip", "LinphoneManager-globalState:" + state + "message:" + message);
        if (state == LinphoneCore.GlobalState.GlobalOn) {
            try {
                initLiblinphone(lc);
            } catch (IllegalArgumentException | LinphoneCoreException iae) {
                LinLog.e("dds_voip", "LinphoneManager-globalState:" + iae.toString());
            }
        }
    }


    @Override
    public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {

    }

    @Override
    public void configuringStatus(LinphoneCore lc, LinphoneCore.RemoteProvisioningState state, String message) {

    }

    @Override
    public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {

    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {

    }

    private LinphoneCall ringingCall;
    private boolean mAudioFocused;

    @Override
    public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
        LinLog.e("dds_test", "New call state [" + state + "]," + message);

        if (state == LinphoneCall.State.IncomingReceived && !call.equals(lc.getCurrentCall())) {
            if (call.getReplacedCall() != null) {
                return;
            }
        }
        if (state == LinphoneCall.State.IncomingReceived && VoipHelper.isInCall) {
            if (mLc != null) {
                LinLog.d("dds_test", "current:" + mLc.getCurrentCall().toString() + ",call:" + call.toString());
                if (mLc.getCurrentCall() == call) {
                    // 要拨打的人来电话了

                } else {
                    // 通话过程中有人来电话了
                    mLc.declineCall(call, Reason.Busy);
                    // 插入一条消息
                    LinphoneAddress remoteAddress = call.getRemoteAddress();
                    String userId = remoteAddress.getUserName();
                    boolean isRemoteVideoEnabe = call.getRemoteParams().getVideoEnabled();
                    VoipCallBack callBack = VoipService.instance.getCallBack();
                    if (callBack != null) {
                        callBack.terminateIncomingCall(isRemoteVideoEnabe, userId, mContext.getString(R.string.voice_chat_incall_miss), true);
                    }
                }
                return;
            }
        }
        if (state == LinphoneCall.State.IncomingReceived && getCallGsmON()) {
            if (mLc != null) {
                mLc.declineCall(call, Reason.Busy);
                return;
            }
        } else if (state == LinphoneCall.State.IncomingReceived || state == LinphoneCall.State.CallIncomingEarlyMedia) {
            //正在打电话的过程中呼入电话
            LinphoneCall currentCall = mLc.getCurrentCall();
            if (null != currentCall && call != currentCall) {
                mLc.declineCall(call, Reason.Busy);
                return;
            }
            if (mLc.getCallsNb() == 1) {
                requestAudioFocus(STREAM_RING);
                ringingCall = call;
                LinLog.e(VoipHelper.TAG, "callState-->startRinging");
                startRinging();
            }
        } else if (call == ringingCall && isRinging) {
            //previous state was ringing, so stop ringing
            LinLog.e(VoipHelper.TAG, "callState-->stopRinging");
            stopRinging();
        }
        if (state == LinphoneCall.State.Connected) {
            if ((mLc != null ? mLc.getCallsNb() : 0) == 1) {
                if (call.getDirection() == CallDirection.Incoming) {

                    setAudioManagerInCallMode();
                    //mAudioManager.abandonAudioFocus(null);
                    requestAudioFocus(STREAM_VOICE_CALL);
                }
            }

            if (Hacks.needSoftvolume()) {
                org.linphone.mediastream.Log.w("Using soft volume audio hack");
                adjustVolume(0); // Synchronize
            }
        }

        if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error) {
            if (mLc != null && mLc.getCallsNb() == 0) {
                if (mAudioFocused) {
                    int res = mAudioManager.abandonAudioFocus(null);
                    org.linphone.mediastream.Log.d("Audio focus released a bit later: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
                    mAudioFocused = false;
                }
                if (mContext != null) {
                    TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm != null && tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                        org.linphone.mediastream.Log.d("---AudioManager: back to MODE_NORMAL");
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);
                        org.linphone.mediastream.Log.d("All call terminated, routing back to earpiece");
                        routeAudioToReceiver();
                    }
                }

            }
        }

        if (state == LinphoneCall.State.CallUpdatedByRemote) {
            // If the correspondent proposes video while audio call
            boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
            boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
            if (remoteVideo && !localVideo && !LinphoneManager.getLc().isInConference()) {
                try {
                    LinphoneManager.getLc().deferCallUpdate(call);
                } catch (LinphoneCoreException e) {
                    org.linphone.mediastream.Log.e(e);
                }
            }
        }

        if (state == LinphoneCall.State.OutgoingInit) {
            setAudioManagerInCallMode();
            requestAudioFocus(STREAM_VOICE_CALL);
        }

        if (state == LinphoneCall.State.StreamsRunning) {
            setAudioManagerInCallMode();
        }


    }

    private void requestAudioFocus(int stream) {
        if (!mAudioFocused) {
            int res = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                res = mAudioManager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            }
            org.linphone.mediastream.Log.d("Audio focus requested: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
        }
    }

    private boolean isRinging;

    public synchronized void startRinging() {
        int readExternalStorage = PackageManager.PERMISSION_DENIED;
        readExternalStorage = mContext.getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, mContext.getPackageName());
        if (readExternalStorage != PackageManager.PERMISSION_GRANTED) {
            routeAudioToSpeaker();
            return;
        }
        routeAudioToSpeaker();
        mAudioManager.setMode(MODE_RINGTONE);
        try {
            if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && mVibrator != null) {
                long[] patern = {0, 1000, 1000};
                mVibrator.vibrate(patern, 1);
            }
            if (mRingerPlayer == null) {
                requestAudioFocus(STREAM_RING);
                mRingerPlayer = new MediaPlayer();
                mRingerPlayer.setAudioStreamType(STREAM_RING);
                String ringtone = Settings.System.DEFAULT_RINGTONE_URI.toString();
                try {
                    if (ringtone.startsWith("content://")) {
                        mRingerPlayer.setDataSource(mContext, Uri.parse(ringtone));
                    } else {
                        FileInputStream fis = new FileInputStream(ringtone);
                        mRingerPlayer.setDataSource(fis.getFD());
                        fis.close();
                    }
                } catch (IOException e) {
                    org.linphone.mediastream.Log.e(e, "Cannot set ringtone");
                }

                mRingerPlayer.prepare();
                mRingerPlayer.setLooping(true);
                mRingerPlayer.start();
            } else {
                org.linphone.mediastream.Log.w("already ringing");
            }
        } catch (Exception e) {
            org.linphone.mediastream.Log.e(e, "cannot handle incoming call");
        }
        isRinging = true;
    }

    public synchronized void stopRinging() {
        if (mRingerPlayer != null) {
            mRingerPlayer.stop();
            mRingerPlayer.release();
            mRingerPlayer = null;
        }
        if (mVibrator != null) {
            mVibrator.cancel();
        }

        if (Hacks.needGalaxySAudioHack())
            mAudioManager.setMode(AudioManager.MODE_NORMAL);

        isRinging = false;
        routeAudioToReceiver();
    }


    public void routeAudioToReceiver() {
        routeAudioToSpeakerHelper(false);
    }

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        org.linphone.mediastream.Log.w("Routing audio to " + (speakerOn ? "speaker" : "earpiece") + ", disabling bluetooth audio route");
        mLc.enableSpeaker(speakerOn);
    }


    public void setAudioManagerInCallMode() {
        if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            org.linphone.mediastream.Log.w("[AudioManager] already in MODE_IN_COMMUNICATION, skipping...");
            return;
        }
        org.linphone.mediastream.Log.d("[AudioManager] Mode: MODE_IN_COMMUNICATION");
        LinLog.e(VoipHelper.TAG, "callState-->setMode:MODE_IN_COMMUNICATION");
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
    private static final int dbStep = 4;

    public void adjustVolume(int i) {
        if (Build.VERSION.SDK_INT < 15) {
            int oldVolume = mAudioManager.getStreamVolume(LINPHONE_VOLUME_STREAM);
            int maxVolume = mAudioManager.getStreamMaxVolume(LINPHONE_VOLUME_STREAM);

            int nextVolume = oldVolume + i;
            if (nextVolume > maxVolume) nextVolume = maxVolume;
            if (nextVolume < 0) nextVolume = 0;

            mLc.setPlaybackGain((nextVolume - maxVolume) * dbStep);
        } else
            // starting from ICS, volume must be adjusted by the application, at least for STREAM_VOICE_CALL volume stream
            mAudioManager.adjustStreamVolume(LINPHONE_VOLUME_STREAM, i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
    }


    @Override
    public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call, boolean encrypted, String authenticationToken) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {

    }

    @Override
    public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore lc, LinphoneCore.EcCalibratorStatus status, int delay_ms, Object data) {
        AudioManager systemService = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (systemService != null) {
            systemService.setMode(AudioManager.MODE_NORMAL);
        }
        mAudioManager.abandonAudioFocus(null);
    }

    @Override
    public void uploadProgressIndication(LinphoneCore lc, int offset, int total) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore lc, LinphoneCore.LogCollectionUploadState state, String info) {

    }

    @Override
    public void friendListCreated(LinphoneCore lc, LinphoneFriendList list) {

    }

    @Override
    public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list) {

    }

    @Override
    public void networkReachableChanged(LinphoneCore lc, boolean enable) {

    }


    private boolean callGsmON;

    public boolean getCallGsmON() {
        return callGsmON;
    }

    public void setCallGsmON(boolean on) {
        callGsmON = on;
    }

}
