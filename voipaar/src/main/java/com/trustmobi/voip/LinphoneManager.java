package com.trustmobi.voip;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.trustmobi.voip.utils.LinphoneUtils;
import com.trustmobi.voip.voipaar.R;

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
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.OpenH264DownloadHelperListener;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.tools.OpenH264DownloadHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

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
    private final String mCallLogDatabaseFile;
    private final String mFriendsDatabaseFile;
    private final String mErrorToneFile;
    private final String mUserCertificatePath;


    private static LinphoneManager instance;
    private LinphoneCore mLc;
    private Context mContext;
    private Resources mR;

    private ConnectivityManager mConnectivityManager;


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
        mCallLogDatabaseFile = basePath + "/linphone-log-history.db";
        mFriendsDatabaseFile = basePath + "/linphone-friends.db";
        mErrorToneFile = basePath + "/error.wav";
        mUserCertificatePath = basePath;

        mR = c.getResources();

        mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

    }


    public synchronized static final LinphoneManager createAndStart(Context c) {
        if (instance != null)
            throw new RuntimeException("Linphone Manager is already initialized");

        instance = new LinphoneManager(c);
        instance.startLibLinphone(c);
        //instance.initOpenH264DownloadHelper();

        // H264 codec Management - set to auto mode -> MediaCodec >= android 5.0 >= OpenH264
        //H264Helper.setH264Mode(H264Helper.MODE_AUTO, getLc());


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
        mLc.setZrtpSecretsCache(basePath + "/zrtp_secrets");
        try {
            String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
            }
            mLc.setUserAgent("LinphoneAndroid", versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("dds", "cannot get version name");
        }

        mLc.setRingback(mRingbackSoundFile);
        mLc.setRootCA(mLinphoneRootCaFile);
        mLc.setPlayFile(mPauseSoundFile);
        mLc.setChatDatabasePath(mChatDatabaseFile);
        mLc.setCallLogsDatabasePath(mCallLogDatabaseFile);
        mLc.setFriendsDatabasePath(mFriendsDatabaseFile);
        mLc.setUserCertificatesPath(mUserCertificatePath);
        mLc.setNetworkReachable(true);
        //mLc.setCallErrorTone(Reason.NotFound, mErrorToneFile);
        enableDeviceRingtone(false);

        int availableCores = Runtime.getRuntime().availableProcessors();
        Log.w("dds", "MediaStreamer : " + availableCores + " cores detected and configured");
        mLc.setCpuCount(availableCores);
        mLc.migrateCallLogs();
        resetCameraFromPreferences();

        String sipAddress = "sip:" + VoipHelper.getInstance().getUserName() + "@" + VoipHelper.getInstance().getDomain();
        String password = VoipHelper.getInstance().getPassword();
        LinphoneAddress address = LinphoneCoreFactory.instance().createLinphoneAddress(sipAddress);
        String username = address.getUserName();
        String domain = address.getDomain();
        address.setTransport(LinphoneAddress.TransportType.LinphoneTransportUdp);
        if (password != null) {
            // create authentication structure from identity and add to linphone
            mLc.addAuthInfo(LinphoneCoreFactory.instance().createAuthInfo(username, password, null, domain));
        }
        LinphoneProxyConfig proxyCfg = mLc.createProxyConfig(sipAddress, domain, null, true);

        proxyCfg.setExpires(2000);
        proxyCfg.enableQualityReporting(false);
        mLc.addProxyConfig(proxyCfg); // add it to linphone
        mLc.setDefaultProxyConfig(proxyCfg);

        callGsmON = false;
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


    private OpenH264DownloadHelper mCodecDownloader;
    private OpenH264DownloadHelperListener mCodecListener;
    private Handler mHandler = new Handler();

    public void initOpenH264DownloadHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.i("dds", "Android >= 5.1 we disable the download of OpenH264");
            getLc().enableDownloadOpenH264(false);
            return;
        }

        mCodecDownloader = LinphoneCoreFactory.instance().createOpenH264DownloadHelper();
        mCodecListener = new OpenH264DownloadHelperListener() {
            ProgressDialog progress;
            int ctxt = 0;
            int box = 1;

            @Override
            public void OnProgress(final int current, final int max) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        OpenH264DownloadHelper ohcodec = LinphoneManager.getInstance().getOpenH264DownloadHelper();
                        if (progress == null) {
                            progress = new ProgressDialog((Context) ohcodec.getUserData(ctxt));
                            progress.setCanceledOnTouchOutside(false);
                            progress.setCancelable(false);
                            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        } else if (current <= max) {
                            progress.setMessage("下载OpenH264");
                            progress.setMax(max);
                            progress.setProgress(current);
                            progress.show();
                        } else {
                            progress.dismiss();
                            progress = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
//                                LinphoneManager.getLc().reloadMsPlugins(AssistantActivity.instance().getApplicationInfo().nativeLibraryDir);
//                                AssistantActivity.instance().endDownloadCodec();
                            } else {
                                // We need to restart due to bad android linker
//                                AssistantActivity.instance().restartApplication();
                                mContext.stopService(new Intent(Intent.ACTION_MAIN).setClass(mContext, LinphoneService.class));
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        }
                    }
                });
            }

            @Override
            public void OnError(final String error) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (progress != null) progress.dismiss();
                        AlertDialog.Builder builder = new AlertDialog.Builder((Context) LinphoneManager.getInstance().getOpenH264DownloadHelper().getUserData(ctxt));
                        builder.setMessage("对不起，已出错");
                        builder.setCancelable(false);
                        builder.setNeutralButton("确定", null);
                        builder.show();
                    }
                });
            }
        };
        mCodecDownloader.setOpenH264HelperListener(mCodecListener);
    }

    public OpenH264DownloadHelper getOpenH264DownloadHelper() {
        return mCodecDownloader;
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
        // If to is only a username, try to find the contact to get an alias if existing
        to = to + "@" + VoipHelper.getInstance().getDomain();
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
                    boolean prefVideoEnable = false;
                    boolean prefInitiateWithVideo = false;
                    CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo, isLowBandwidthConnection);
                } else {
                    CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection);
                }
            } catch (LinphoneCoreException e) {
                return;
            }
        }
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
            mLc.acceptCallWithParams(call, params);
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

    @Override
    public void callState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state, String s) {

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
