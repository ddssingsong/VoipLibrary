package com.trustmobi.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

import com.trustmobi.voip.utils.LinphoneUtils;
import com.trustmobi.voip.voipaar.R;

import linphone.linphone.core.CallDirection;
import linphone.linphone.core.LinphoneAddress;
import linphone.linphone.core.LinphoneAuthInfo;
import linphone.linphone.core.LinphoneCall;
import linphone.linphone.core.LinphoneCallParams;
import linphone.linphone.core.LinphoneCallStats;
import linphone.linphone.core.LinphoneChatMessage;
import linphone.linphone.core.LinphoneChatRoom;
import linphone.linphone.core.LinphoneContent;
import linphone.linphone.core.LinphoneCore;
import linphone.linphone.core.LinphoneCoreException;
import linphone.linphone.core.LinphoneCoreFactory;
import linphone.linphone.core.LinphoneCoreListener;
import linphone.linphone.core.LinphoneEvent;
import linphone.linphone.core.LinphoneFriend;
import linphone.linphone.core.LinphoneFriendList;
import linphone.linphone.core.LinphoneInfoMessage;
import linphone.linphone.core.LinphoneNatPolicy;
import linphone.linphone.core.LinphoneProxyConfig;
import linphone.linphone.core.PresenceBasicStatus;
import linphone.linphone.core.PublishState;
import linphone.linphone.core.Reason;
import linphone.linphone.core.SubscriptionState;
import linphone.linphone.mediastream.Version;
import linphone.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import linphone.linphone.mediastream.video.capture.hwconf.Hacks;
import linphone.linphone.tools.H264Helper;

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
 * Created by dds on 2018/3/17 0017.
 */

public class LinphoneManager implements LinphoneCoreListener {


    private final String mLPConfigXsd;
    private final String mLinphoneFactoryConfigFile;
    private final String mLinphoneRootCaFile;
    private final String mDynamicConfigFile;
    public final String mLinphoneConfigFile;
    private final String mRingSoundFile;
    private final String mRingbackSoundFile;
    private final String mPauseSoundFile;
    private final String mChatDatabaseFile;
    private final String mErrorToneFile;
    private final String mUserCertificatePath;


    private static LinphoneManager instance;
    private LinphoneCore mLc;
    private Context mContext;
    private Resources mR;

    private ConnectivityManager mConnectivityManager;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private MediaPlayer mRingerPlayer;


    protected LinphoneManager(final Context c) {
        mContext = c;
        basePath = c.getFilesDir().getAbsolutePath();
        mLPConfigXsd = basePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = basePath + "/linphonerc";
        mLinphoneConfigFile = basePath + "/.linphonerc";
        mLinphoneRootCaFile = basePath + "/rootca.pem";
        mDynamicConfigFile = basePath + "/assistant_create.rc";
        mRingSoundFile = basePath + "/ringtone.mkv";
        mRingbackSoundFile = basePath + "/ringback.wav";
        mPauseSoundFile = basePath + "/hold.mkv";
        mChatDatabaseFile = basePath + "/linphone-history.db";
        mErrorToneFile = basePath + "/error.wav";
        mUserCertificatePath = basePath;

        mR = c.getResources();

        mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);

    }


    public synchronized static final LinphoneManager createAndStart(Context c) {
        if (instance != null)
            throw new RuntimeException("Linphone Manager is already initialized");

        instance = new LinphoneManager(c);
        instance.startLibLinphone(c);
        //instance.initOpenH264DownloadHelper();

        // H264 codec Management - set to auto mode -> MediaCodec >= android 5.0 >= OpenH264
        H264Helper.setH264Mode(H264Helper.MODE_MEDIA_CODEC, getLc());


        return instance;
    }

    public static synchronized final LinphoneManager getInstance() {
        if (instance != null) return instance;
        throw new RuntimeException("Linphone Manager should be created before accessed");
    }

    public static synchronized final LinphoneCore getLc() {
        return getInstance().mLc;
    }

    public static final boolean isInstanciated() {
        return instance != null;
    }

    public static synchronized LinphoneCore getLcIfManagerNotDestroyedOrNull() {
        if (instance == null) {
            return null;
        }
        return getLc();
    }

    private Timer mTimer;

    private synchronized void startLibLinphone(Context c) {
        try {
            copyAssetsFromPackage();
            //traces alway start with traces enable to not missed first initialization
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
            /*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
            mTimer = new Timer("Linphone scheduler");
            mTimer.schedule(lTask, 0, 20);
        } catch (Exception e) {
            Log.e("dds", "Cannot start linphone");
        }
    }

    private String basePath;

    private synchronized void initLiblinphone(LinphoneCore lc) throws LinphoneCoreException {
        mLc = lc;
        mLc.setRingback(mRingbackSoundFile);
        mLc.setRootCA(mLinphoneRootCaFile);
        mLc.setPlayFile(mPauseSoundFile);
        mLc.setChatDatabasePath(mChatDatabaseFile);
        mLc.setUserCertificatesPath(mUserCertificatePath);
        mLc.setNetworkReachable(true);
        mLc.enableEchoCancellation(true);
        enableDeviceRingtone(true);

        int availableCores = Runtime.getRuntime().availableProcessors();
        mLc.setCpuCount(availableCores);
        mLc.migrateCallLogs();
        //设置加密方式，如果改为Srtp则使用自己改的加密
        LinphoneCore.MediaEncryption menc = LinphoneCore.MediaEncryption.SRTP;
        mLc.setMediaEncryption(menc);


        resetCameraFromPreferences();

        Log.e("dds_test", "initLiblinphone----------------------");
        callGsmON = false;
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


    private LinphoneNatPolicy getOrCreateNatPolicy() {
        LinphoneNatPolicy nat = getLc().getNatPolicy();
        if (nat == null) {
            nat = getLc().createNatPolicy();
        }
        return nat;
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

    public void enableDeviceRingtone(boolean use) {
        if (use) {
            mLc.setRing(null);
        } else {
            mLc.setRing(mRingSoundFile);
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
        copyFromPackage(R.raw.assistant_create, new File(mDynamicConfigFile).getName());
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
            linphone.linphone.mediastream.Log.e(e);
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
            linphone.linphone.mediastream.Log.e(e);
        }

        return null;
    }


    private boolean callGsmON;

    public boolean getCallGsmON() {
        return callGsmON;
    }

    public void setCallGsmON(boolean on) {
        callGsmON = on;
    }

    public void newOutgoingCall(String to) {
        newOutgoingCall(to, to);
    }

    public void newOutgoingCall(String to, String displayName) {
        if (to == null) return;
        to = to + "@" + mLc.getDefaultProxyConfig().getDomain();
        Log.e("dds", "call to number:" + to);
        LinphoneAddress lAddress;
        try {
            lAddress = mLc.interpretUrl(to);

        } catch (LinphoneCoreException e) {
            Log.e("dds", e.toString());
            return;
        }
        lAddress.setDisplayName(displayName);
        mLc.enableSpeaker(false);
        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());
        if (mLc.isNetworkReachable()) {
            try {
                if (Version.isVideoCapable()) {
                    boolean prefVideoEnable = getLc().isVideoSupported() && getLc().isVideoEnabled();
                    boolean prefInitiateWithVideo = getLc().getVideoAutoInitiatePolicy();
                    CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo, isLowBandwidthConnection);
                } else {
                    CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection);
                }
            } catch (LinphoneCoreException e) {
                e.printStackTrace();
            }
        }
    }


    public void setMediaEncryption(LinphoneCore.MediaEncryption menc) {
        if (mLc != null) {
            mLc.setMediaEncryption(menc);
        }
    }

    public LinphoneCore.MediaEncryption getMediaEncryption() {
        if (mLc != null) {
            return mLc.getMediaEncryption();
        }
        return LinphoneCore.MediaEncryption.None;
    }

    public static synchronized void destroy() {
        if (instance == null) return;
        getInstance().changeStatusToOffline();
        instance.doDestroy();
    }

    public void changeStatusToOffline() {
        LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
        if (isInstanciated() && lc != null) {
            lc.getPresenceModel().setBasicStatus(PresenceBasicStatus.Closed);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void doDestroy() {
        try {
            mTimer.cancel();
            mLc.clearProxyConfigs();
            mLc.clearAuthInfos();
            mLc.destroy();
        } catch (RuntimeException e) {
            Log.e("dds", e.toString());
        } finally {
            mLc = null;
            instance = null;
        }
    }


    public boolean acceptCallWithParams(LinphoneCall call, LinphoneCallParams params) {
        try {
            mLc.acceptCallWithParams(call, params, "1234567890123456789012345678901234567890");
            return true;
        } catch (LinphoneCoreException e) {
            Log.i("dds", "Accept call failed");
        }
        return false;
    }


    @Override
    public void authInfoRequested(LinphoneCore linphoneCore, String s, String s1, String s2) {

    }

    @Override
    public void authenticationRequested(LinphoneCore linphoneCore, LinphoneAuthInfo linphoneAuthInfo, LinphoneCore.AuthMethod authMethod) {

    }

    @Override
    public void callStatsUpdated(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCallStats linphoneCallStats) {

    }

    @Override
    public void newSubscriptionRequest(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend, String s) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend) {

    }

    @Override
    public void dtmfReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, int i) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneAddress linphoneAddress, byte[] bytes) {

    }

    @Override
    public void transferState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state) {

    }

    @Override
    public void infoReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneInfoMessage linphoneInfoMessage) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, SubscriptionState subscriptionState) {

    }

    @Override
    public void publishStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, PublishState publishState) {

    }

    @Override
    public void show(LinphoneCore linphoneCore) {

    }

    @Override
    public void displayStatus(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void displayMessage(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void displayWarning(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void fileTransferProgressIndication(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i) {

    }

    @Override
    public void fileTransferRecv(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, byte[] bytes, int i) {

    }

    @Override
    public int fileTransferSend(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, ByteBuffer byteBuffer, int i) {
        return 0;
    }

    @Override
    public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String s) {
        if (state == LinphoneCore.GlobalState.GlobalOn) {
            try {
                Log.e("LinphoneManager", " globalState ON");
                initLiblinphone(lc);

            } catch (IllegalArgumentException iae) {
                Log.e("dds", iae.toString());
            } catch (LinphoneCoreException e) {
                Log.e("dds", e.toString());
            }
        }
    }

    @Override
    public void registrationState(LinphoneCore linphoneCore, LinphoneProxyConfig linphoneProxyConfig, LinphoneCore.RegistrationState registrationState, String s) {

    }

    @Override
    public void configuringStatus(LinphoneCore linphoneCore, LinphoneCore.RemoteProvisioningState remoteProvisioningState, String s) {

    }

    @Override
    public void messageReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {

    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {

    }

    private LinphoneCall ringingCall;
    private boolean mAudioFocused;

    @Override
    public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
        LinLog.e(VoipHelper.VOIP_TAG, "New call state [" + state + "]");
        if (state == LinphoneCall.State.IncomingReceived && !call.equals(lc.getCurrentCall())) {
            if (call.getReplacedCall() != null) {
                return;
            }
        }
        if (state == LinphoneCall.State.IncomingReceived && getCallGsmON()) {
            if (mLc != null) {
                mLc.declineCall(call, Reason.Busy);
            }
        } else if (state == LinphoneCall.State.IncomingReceived || (state == LinphoneCall.State.CallIncomingEarlyMedia)) {
            if (mLc.getCallsNb() == 1) {
                requestAudioFocus(STREAM_RING);
                ringingCall = call;
                LinLog.e(VoipHelper.VOIP_TAG, "callState-->startRinging");
                startRinging();
            }
        } else if (call == ringingCall && isRinging) {
            //previous state was ringing, so stop ringing
            LinLog.e(VoipHelper.VOIP_TAG, "callState-->stopRinging");
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
                linphone.linphone.mediastream.Log.w("Using soft volume audio hack");
                adjustVolume(0); // Synchronize
            }
        }

        if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error) {
            if (mLc != null && mLc.getCallsNb() == 0) {
                if (mAudioFocused) {
                    int res = mAudioManager.abandonAudioFocus(null);
                    linphone.linphone.mediastream.Log.d("Audio focus released a bit later: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
                    mAudioFocused = false;
                }

            }
        }

        if (state == LinphoneCall.State.CallUpdatedByRemote) {
            // If the correspondent proposes video while audio call
            boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
            boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
            boolean autoAcceptCameraPolicy = false;
            if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
                try {
                    LinphoneManager.getLc().deferCallUpdate(call);
                } catch (LinphoneCoreException e) {
                    linphone.linphone.mediastream.Log.e(e);
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
            linphone.linphone.mediastream.Log.d("Audio focus requested: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
        }
    }

    private boolean isRinging;

    private synchronized void startRinging() {
        int readExternalStorage = PackageManager.PERMISSION_DENIED;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            readExternalStorage = mContext.getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, mContext.getPackageName());
        }
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
                    linphone.linphone.mediastream.Log.e(e, "Cannot set ringtone");
                }

                mRingerPlayer.prepare();
                mRingerPlayer.setLooping(true);
                mRingerPlayer.start();
            } else {
                linphone.linphone.mediastream.Log.w("already ringing");
            }
        } catch (Exception e) {
            linphone.linphone.mediastream.Log.e(e, "cannot handle incoming call");
        }
        isRinging = true;
    }

    private synchronized void stopRinging() {
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
        linphone.linphone.mediastream.Log.w("Routing audio to " + (speakerOn ? "speaker" : "earpiece") + ", disabling bluetooth audio route");
        mLc.enableSpeaker(speakerOn);
    }


    public void setAudioManagerInCallMode() {
        if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            linphone.linphone.mediastream.Log.w("[AudioManager] already in MODE_IN_COMMUNICATION, skipping...");
            return;
        }
        linphone.linphone.mediastream.Log.d("[AudioManager] Mode: MODE_IN_COMMUNICATION");
        LinLog.e(VoipHelper.VOIP_TAG, "callState-->setMode:MODE_IN_COMMUNICATION");
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
    public void callEncryptionChanged(LinphoneCore linphoneCore, LinphoneCall linphoneCall, boolean b, String s) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, String s, LinphoneContent linphoneContent) {

    }

    @Override
    public void isComposingReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore linphoneCore, LinphoneCore.EcCalibratorStatus ecCalibratorStatus, int i, Object o) {

    }

    @Override
    public void uploadProgressIndication(LinphoneCore linphoneCore, int i, int i1) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore linphoneCore, LinphoneCore.LogCollectionUploadState logCollectionUploadState, String s) {

    }

    @Override
    public void friendListCreated(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void friendListRemoved(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void networkReachableChanged(LinphoneCore linphoneCore, boolean b) {

    }
}
