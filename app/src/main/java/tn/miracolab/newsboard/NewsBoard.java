package tn.miracolab.newsboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by Vincentnien on 15/5/18.
 */
public class NewsBoard {

    private static final String TAG = NewsBoard.class.getSimpleName();
    private static final String SHARD_PREF = "update_message";

    public enum CheckMethod {
        LAST_MODIFIED,
        GITHUB_ETAG,
        MD5
    }

    private WeakReference<Activity> activity;

    private String url;
    private int negativeId = -1;
    private int positiveId = -1;

    private int titleId = -1;

    private DialogInterface.OnClickListener negativeClickListener = null;
    private DialogInterface.OnClickListener positiveClickListener = null;

    private CheckMethod method = CheckMethod.LAST_MODIFIED;

    private static class NewsMessage {
        boolean isNew;
        String message;
        String data;

        private NewsMessage() {
            isNew = false;
        }

        public static NewsMessage empty() {
            return new NewsMessage();
        }

        public static NewsMessage create(boolean isNew, String message, String lastModified) {
            return new NewsMessage(isNew, message, lastModified);
        }

        private NewsMessage(boolean isNew, String message, String data) {
            this.isNew = isNew;
            this.message = message;
            this.data = data;
        }
    }

    private NewsBoard(Activity activity) {
        this.activity = new WeakReference<Activity>(activity);
    }

    public NewsBoard withUrl(String url) {
        this.url = url;
        return this;
    }

    public NewsBoard addPositiveButton(int id, DialogInterface.OnClickListener listener) {
        positiveId = id;
        positiveClickListener = listener;
        return this;
    }

    public NewsBoard addNegativeButton(int id, DialogInterface.OnClickListener listener) {
        negativeId = id;
        negativeClickListener = listener;
        return this;
    }

    public NewsBoard checkMethod(CheckMethod method) {
        this.method = method;
        return this;
    }

    public NewsBoard titleId(int titleId) {
        this.titleId = titleId;
        return this;
    }

    public void run() throws IOException {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("url cannot be empty.");
        }

        final NewsMessage msg = readMessage();
        if ( msg.isNew ) {
            // update last modified time
            SharedPreferences pref = activity.get().getSharedPreferences(SHARD_PREF, Context.MODE_PRIVATE);
            pref.edit().putString("lastModified", msg.data).apply();

            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity.get());
                    builder.setMessage(msg.message).setCancelable(true);
                    if ( titleId != -1 ) {
                        builder.setTitle(titleId);
                    }
                    if ( negativeId != -1 ) {
                        builder.setNegativeButton(negativeId, negativeClickListener);
                    }
                    if ( positiveId != -1 ) {
                        builder.setPositiveButton(positiveId, positiveClickListener);
                    }
                    builder.create().show();
                }
            });
        }
    }

    private NewsMessage readMessage() {
        try {
            Activity ref = activity.get();
            SharedPreferences pref = ref.getSharedPreferences(SHARD_PREF, Context.MODE_PRIVATE);
            String lastdataInPref = pref.getString("lastModified", "");

            URL urlObj = new URL(url);
            URLConnection connection = urlObj.openConnection();
            String fromHeader = null;
            if (method == CheckMethod.LAST_MODIFIED) {
                fromHeader = "Last-Modified";
            } else if ( method == CheckMethod.GITHUB_ETAG ) {
                fromHeader = "ETag";
            } else {
                // not supported for now
            }

            if ( fromHeader != null ) {
                String data = connection.getHeaderField(fromHeader);
                if (!lastdataInPref.equals(data)) {
                    Log.e(TAG, "" + data);
                    // only support text
                    StringBuilder sb = new StringBuilder();
                    BufferedReader in = null;
                    try {
                        in = new BufferedReader(new InputStreamReader(
                                connection.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null)
                            sb.append(inputLine).append("\n");
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                    }
                    return NewsMessage.create(true, sb.toString(), data);
                }
            }
        } catch(IOException e) {
            Log.e(TAG, e.toString(), e);
        }
        return NewsMessage.empty();
    }

    public static NewsBoard create(Activity activity) {
        return new NewsBoard(activity);
    }
}
