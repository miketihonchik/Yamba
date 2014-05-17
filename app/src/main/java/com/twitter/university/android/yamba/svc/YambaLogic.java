package com.twitter.university.android.yamba.svc;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.marakana.android.yamba.clientlib.YambaClient.Status;
import com.marakana.android.yamba.clientlib.YambaClientException;
import com.twitter.university.android.yamba.BuildConfig;
import com.twitter.university.android.yamba.R;
import com.twitter.university.android.yamba.YambaApplication;
import com.twitter.university.android.yamba.YambaContract;

import java.util.ArrayList;
import java.util.List;


class YambaLogic {
    private static final String TAG = "LOGIC";

    private static final int OP_TOAST = -3;

    private final YambaApplication app;
    private final int maxPolls;
    private final Handler hdlr;

    // Must be called from the UI thread
    public YambaLogic(final YambaApplication app, int maxPolls) {
        this.app = app;
        this.maxPolls = maxPolls;
        hdlr = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case OP_TOAST:
                        Toast.makeText(app, msg.arg1, Toast.LENGTH_LONG).show();
                        break;
                }
            }
        };
    }

    public void doPost(String tweet) {
        int msg = R.string.tweet_succeeded;
        try { app.getClient().postStatus(tweet); }
        catch (YambaClientException e) {
                Log.e(TAG, "post failed", e);
                msg = R.string.tweet_failed;
            }
        hdlr.obtainMessage(OP_TOAST, msg, 0).sendToTarget();
    }

    public void doPoll() {
        Log.d(TAG, "poll");
        try {
            parseTimeline(app.getClient().getTimeline(maxPolls));
        }
        catch (Exception e) {
            Log.e(TAG, "Poll failed: " + e, e);
        }
    }

    private int parseTimeline(List<Status> timeline) {
        long latest = getMaxTimestamp();

        List<ContentValues> vals = new ArrayList<ContentValues>();
        for (Status tweet: timeline) {
            long t = tweet.getCreatedAt().getTime();
            if (t <= latest) { continue; }

            ContentValues row = new ContentValues();
            row.put(YambaContract.Timeline.Columns.ID, Long.valueOf(tweet.getId()));
            row.put(YambaContract.Timeline.Columns.TIMESTAMP, Long.valueOf(t));
            row.put(YambaContract.Timeline.Columns.HANDLE, tweet.getUser());
            row.put(YambaContract.Timeline.Columns.TWEET, tweet.getMessage());
            vals.add(row);
        }

        int n = vals.size();
        if (0 >= n) { return 0; }
        n = app.getContentResolver().bulkInsert(
            YambaContract.Timeline.URI,
            vals.toArray(new ContentValues[n]));

        if (BuildConfig.DEBUG) { Log.d(TAG, "inserted: " + n); }
        return n;
    }

    private long getMaxTimestamp() {
        Cursor c = null;
        try {
            c = app.getContentResolver().query(
                YambaContract.MaxTimeline.URI,
                null,
                null,
                null,
                null);
            return ((null == c) || (!c.moveToNext()))
                ? Long.MIN_VALUE
                : c.getLong(0);
        }
        finally {
            if (null != c) { c.close(); }
        }
    }
}
