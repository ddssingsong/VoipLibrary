package com.trustmobi.voip.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import org.linphone.core.LinphoneCall;

/**
 * Created by Administrator on 2018/3/18 0018.
 */

public class LinphoneUtils {

    public static boolean isHighBandwidthConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(), info.getSubtype()));
    }

    private static boolean isConnectionFast(int type, int subType) {
        if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return false;
            }
        }
        //in doubt, assume connection is good.
        return true;
    }


    public static boolean isCallEstablished(LinphoneCall call) {
        if (call == null) {
            return false;
        }

        LinphoneCall.State state = call.getState();

        return isCallRunning(call) ||
                state == LinphoneCall.State.Paused ||
                state == LinphoneCall.State.PausedByRemote ||
                state == LinphoneCall.State.Pausing;
    }

    public static boolean isCallRunning(LinphoneCall call) {
        if (call == null) {
            return false;
        }

        LinphoneCall.State state = call.getState();

        return state == LinphoneCall.State.Connected ||
                state == LinphoneCall.State.CallUpdating ||
                state == LinphoneCall.State.CallUpdatedByRemote ||
                state == LinphoneCall.State.StreamsRunning ||
                state == LinphoneCall.State.Resuming;
    }
}
