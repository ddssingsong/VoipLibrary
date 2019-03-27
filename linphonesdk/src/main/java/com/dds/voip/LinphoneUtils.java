package com.dds.voip;

/*
SoftVolume.java
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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.TextView;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.mediastream.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Helpers.
 */
public final class LinphoneUtils {

    private LinphoneUtils() {
    }

    public static boolean isSipAddress(String numberOrAddress) {
        try {
            LinphoneCoreFactory.instance().createLinphoneAddress(numberOrAddress);
            return true;
        } catch (LinphoneCoreException e) {
            return false;
        }
    }

    public static boolean isStrictSipAddress(String numberOrAddress) {
        return isSipAddress(numberOrAddress) && numberOrAddress.startsWith("sip:");
    }

    public static String getAddressDisplayName(String uri) {
        LinphoneAddress lAddress;
        try {
            lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(uri);
            return getAddressDisplayName(lAddress);
        } catch (LinphoneCoreException e) {
            return null;
        }
    }

    public static String getAddressDisplayName(LinphoneAddress address) {
        if (address.getDisplayName() != null) {
            return address.getDisplayName();
        } else {
            if (address.getUserName() != null) {
                return address.getUserName();
            } else {
                return address.asStringUriOnly();
            }
        }
    }

    public static String getUsernameFromAddress(String address) {
        if (address.contains("sip:"))
            address = address.replace("sip:", "");

        if (address.contains("@"))
            address = address.split("@")[0];

        return address;
    }

    public static boolean onKeyBackGoHome(Activity activity, int keyCode, KeyEvent event) {
        if (!(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)) {
            return false; // continue
        }

        activity.startActivity(new Intent()
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME));
        return true;
    }


    static boolean isToday(Calendar cal) {
        return isSameDay(cal, Calendar.getInstance());
    }

    static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }


    public static Bitmap downloadBitmap(Uri uri) {
        URL url;
        InputStream is = null;
        try {
            url = new URL(uri.toString());
            is = url.openStream();
            return BitmapFactory.decodeStream(is);
        } catch (MalformedURLException e) {
            Log.e(e, e.getMessage());
        } catch (IOException e) {
            Log.e(e, e.getMessage());
        } finally {
            try {
                is.close();
            } catch (IOException x) {
            }
        }
        return null;
    }


    public static final List<LinphoneCall> getLinphoneCallsNotInConf(LinphoneCore lc) {
        List<LinphoneCall> l = new ArrayList<LinphoneCall>();
        for (LinphoneCall c : lc.getCalls()) {
            if (!c.isInConference()) {
                l.add(c);
            }
        }
        return l;
    }

    public static final List<LinphoneCall> getLinphoneCallsInConf(LinphoneCore lc) {
        List<LinphoneCall> l = new ArrayList<LinphoneCall>();
        for (LinphoneCall c : lc.getCalls()) {
            if (c.isInConference()) {
                l.add(c);
            }
        }
        return l;
    }

    public static final List<LinphoneCall> getLinphoneCalls(LinphoneCore lc) {
        // return a modifiable list
        return new ArrayList<LinphoneCall>(Arrays.asList(lc.getCalls()));
    }

    public static final boolean hasExistingResumeableCall(LinphoneCore lc) {
        for (LinphoneCall c : getLinphoneCalls(lc)) {
            if (c.getState() == State.Paused) {
                return true;
            }
        }
        return false;
    }

    public static final List<LinphoneCall> getCallsInState(LinphoneCore lc, Collection<State> states) {
        List<LinphoneCall> foundCalls = new ArrayList<LinphoneCall>();
        for (LinphoneCall call : getLinphoneCalls(lc)) {
            if (states.contains(call.getState())) {
                foundCalls.add(call);
            }
        }
        return foundCalls;
    }

    public static final List<LinphoneCall> getRunningOrPausedCalls(LinphoneCore lc) {
        return getCallsInState(lc, Arrays.asList(
                State.Paused,
                State.PausedByRemote,
                State.StreamsRunning));
    }

    public static final int countConferenceCalls(LinphoneCore lc) {
        int count = lc.getConferenceSize();
        if (lc.isInConference()) count--;
        return count;
    }

    public static int countVirtualCalls(LinphoneCore lc) {
        return lc.getCallsNb() - countConferenceCalls(lc);
    }

    public static int countNonConferenceCalls(LinphoneCore lc) {
        return lc.getCallsNb() - countConferenceCalls(lc);
    }

    public static void setVisibility(View v, int id, boolean visible) {
        v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
    }

    public static void setVisibility(View v, boolean visible) {
        v.setVisibility(visible ? VISIBLE : GONE);
    }

    public static void enableView(View root, int id, OnClickListener l, boolean enable) {
        View v = root.findViewById(id);
        v.setVisibility(enable ? VISIBLE : GONE);
        v.setOnClickListener(l);
    }

    public static int pixelsToDpi(Resources res, int pixels) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) pixels, res.getDisplayMetrics());
    }

    public static boolean isCallRunning(LinphoneCall call) {
        if (call == null) {
            return false;
        }

        State state = call.getState();

        return state == State.Connected ||
                state == State.CallUpdating ||
                state == State.CallUpdatedByRemote ||
                state == State.StreamsRunning ||
                state == State.Resuming;
    }

    public static boolean isCallEstablished(LinphoneCall call) {
        if (call == null) {
            return false;
        }

        State state = call.getState();

        return isCallRunning(call) ||
                state == State.Paused ||
                state == State.PausedByRemote ||
                state == State.Pausing;
    }

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

    public static boolean isNetConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable();
    }

    public static void clearLogs() {
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
        } catch (IOException e) {
            Log.e(e);
        }
    }

    public static boolean zipLogs(StringBuilder sb, String toZipFile) {
        boolean success = false;
        try {
            FileOutputStream zip = new FileOutputStream(toZipFile);

            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(zip));
            ZipEntry entry = new ZipEntry("logs.txt");
            out.putNextEntry(entry);

            out.write(sb.toString().getBytes());

            out.close();
            success = true;

        } catch (Exception e) {
            Log.e("Exception when trying to zip the logs: " + e.getMessage());
        }

        return success;
    }


    public static String getNameFromFilePath(String filePath) {
        String name = filePath;
        int i = filePath.lastIndexOf('/');
        if (i > 0) {
            name = filePath.substring(i + 1);
        }
        return name;
    }

    public static String getExtensionFromFileName(String fileName) {
        String extension = null;
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public static Boolean isExtensionImage(String path) {
        String extension = LinphoneUtils.getExtensionFromFileName(path);
        if (extension != null)
            extension = extension.toLowerCase();
        return (extension != null && extension.matches(".*(png|jpg|jpeg|bmp|gif).*"));
    }

    public static void recursiveFileRemoval(File root) {
        if (!root.delete()) {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File f : files) {
                        recursiveFileRemoval(f);
                    }
                }
            }
        }
    }

    public static void storeImage(Context context, LinphoneChatMessage msg) {
        if (msg == null || msg.getFileTransferInformation() == null || msg.getAppData() == null)
            return;
        File file = new File(Environment.getExternalStorageDirectory(), msg.getAppData());
        Bitmap bm = BitmapFactory.decodeFile(file.getPath());
        if (bm == null) return;

        ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, file.getName());
        String extension = msg.getFileTransferInformation().getSubtype();
        values.put(Images.Media.MIME_TYPE, "image/" + extension);
        ContentResolver cr = context.getContentResolver();
        Uri path = cr.insert(Images.Media.EXTERNAL_CONTENT_URI, values);

        OutputStream stream;
        try {
            stream = cr.openOutputStream(path);
            if (extension != null && extension.toLowerCase(Locale.getDefault()).equals("png")) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } else {
                bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            }

            stream.close();
            file.delete();
            bm.recycle();

            msg.setAppData(path.toString());
        } catch (FileNotFoundException e) {
            Log.e(e);
        } catch (IOException e) {
            Log.e(e);
        }
    }


    public static void displayError(boolean isOk, TextView error, String errorText) {
        if (isOk) {
            error.setVisibility(View.INVISIBLE);
            error.setText("");
        } else {
            error.setVisibility(View.VISIBLE);
            error.setText(errorText);
        }
    }


    public static String getCountryCode(EditText dialCode) {
        if (dialCode != null) {
            String code = dialCode.getText().toString();
            if (code != null && code.startsWith("+")) {
                code = code.substring(1);
            }
            return code;
        }
        return null;
    }

    /************************************************************************************************
     *							Picasa/Photos management workaround									*
     ************************************************************************************************/

    public static String getFilePath(final Context context, final Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            String type = getTypeFromUri(uri, context);
            String result = getDataColumn(context, uri, null, null); //
            if (TextUtils.isEmpty(result))
                if (uri.getAuthority().contains("com.google.android") || uri.getAuthority().contains("com.android")) {
                    try {
                        File localFile = createFile(context, null, type);
                        FileInputStream remoteFile = getSourceStream(context, uri);
                        if (copyToFile(remoteFile, localFile))
                            result = localFile.getAbsolutePath();
                        remoteFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            return result;
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }


    private static String getTypeFromUri(Uri uri, Context context) {
        ContentResolver cR = context.getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String type = mime.getExtensionFromMimeType(cR.getType(uri));
        return type;
    }

    /**
     * Copy data from a source stream to destFile.
     * Return true if succeed, return false if failed.
     */
    private static boolean copyToFile(InputStream inputStream, File destFile) {
        if (inputStream == null || destFile == null) return false;
        try {
            OutputStream out = new FileOutputStream(destFile);
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } finally {
                out.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getTimestamp() {
        try {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        } catch (RuntimeException e) {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        }
    }

    public static File createFile(Context context, String imageFileName, String type) throws IOException {
        if (TextUtils.isEmpty(imageFileName))
            imageFileName = getTimestamp() + "." + type; // make random filename if you want.

        final File root;
        imageFileName = imageFileName;
        root = context.getExternalCacheDir();

        if (root != null && !root.exists())
            root.mkdirs();
        return new File(root, imageFileName);
    }


    public static FileInputStream getSourceStream(Context context, Uri u) throws FileNotFoundException {
        FileInputStream out = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ParcelFileDescriptor parcelFileDescriptor =
                    context.getContentResolver().openFileDescriptor(u, "r");
            FileDescriptor fileDescriptor = null;
            if (parcelFileDescriptor != null) {
                fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                out = new FileInputStream(fileDescriptor);
            }
        } else {
            out = (FileInputStream) context.getContentResolver().openInputStream(u);
        }
        return out;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    static String getDataColumn(Context context, Uri uri, String selection,
                                String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

    public static String getRealPathFromURI(Context context, Uri contentUri) {
        String[] proj = {Images.Media.DATA};
        CursorLoader loader = new CursorLoader(context, contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(Images.Media.DATA);
            String result = cursor.getString(column_index);
            cursor.close();
            return result;
        }
        return null;
    }

    public static String processContactUri(Context context, Uri contactUri) {
        ContentResolver cr = context.getContentResolver();
        InputStream stream = null;
        if (cr != null) {
            try {
                stream = cr.openInputStream(contactUri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (stream != null) {
                StringBuffer fileContent = new StringBuffer("");
                int ch;
                try {
                    while ((ch = stream.read()) != -1)
                        fileContent.append((char) ch);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String data = new String(fileContent);
                return data;
            }
            return null;
        }
        return null;
    }

    public static String getContactNameFromVcard(String vcard) {
        if (vcard != null) {
            String contactName = vcard.substring(vcard.indexOf("FN:") + 3);
            contactName = contactName.substring(0, contactName.indexOf("\n") - 1);
            contactName = contactName.replace(";", "");
            contactName = contactName.replace(" ", "");
            return contactName;
        }
        return null;
    }

    public static Uri createCvsFromString(String vcardString) {
        String contactName = getContactNameFromVcard(vcardString);
        File vcfFile = new File(Environment.getExternalStorageDirectory(), contactName + ".cvs");
        try {
            FileWriter fw = new FileWriter(vcfFile);
            fw.write(vcardString);
            fw.close();
            return Uri.fromFile(vcfFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

