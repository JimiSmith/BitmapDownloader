package za.co.smith.BitmapDownloader;

import android.content.Context;
import android.os.Handler;
import za.co.smith.BitmapDownloader.BitmapDownloaderTask.BitmapDownloadListener;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

class Manager implements Runnable {
    private static String TAG = Manager.class.getCanonicalName();
    private static int MAX_DOWNLOADS = 5;

    interface ManagerDelegate {
        void downloadComplete(String url);

        void downloadFailed(String url);
    }

    private enum Status {
        Completed,
        InProgress,
        Failed,
        Queued,
        Cancelled
    }

    class Download {
        URL url;
        String urlHash;
        Status status;
        Context context;
    }

    private ManagerDelegate mDelegate;
    private ArrayList<Download> mDownloads;
    private Handler mHandler;
    private int mRunningDownloads = 0;

    Manager(ManagerDelegate delegate) {
        mDelegate = delegate;
        mHandler = new Handler();
        mDownloads = new ArrayList<Manager.Download>();
    }

    @Override
    public void run() {
        final Download nextDownload = getNextQueuedDownload();
        if (nextDownload != null) {
            mRunningDownloads++;
            BitmapDownloaderTask downloaderTask = new BitmapDownloaderTask(new BitmapDownloadListener() {

                @Override
                public void onError() {
                    nextDownload.status = Status.Failed;
                    mDelegate.downloadFailed(nextDownload.url.toString());
                    mDownloads.remove(nextDownload);
                    mRunningDownloads--;
                    startNextDownload();
                }

                @Override
                public void onComplete() {
                    nextDownload.status = Status.Completed;
                    mDelegate.downloadComplete(nextDownload.url.toString());
                    mDownloads.remove(nextDownload);
                    mRunningDownloads--;
                    startNextDownload();
                }
            });
            nextDownload.status = Status.InProgress;
            downloaderTask.execute(nextDownload);
        }
    }

    void addDownload(String url, Context context) {
        Download d = new Download();
        try {
            d.url = new URL(url);
            d.urlHash = Utilities.md5(url);
            d.status = Status.Queued;
            d.context = context;
            addToDownloadsIfNotDuplicate(d);
        } catch (MalformedURLException e) {
            mDelegate.downloadFailed(url);
        }
    }

    void cancelDownload(String url) {
        ArrayList<Download> toRemove = new ArrayList<Manager.Download>();
        for (Download d : mDownloads) {
            if (d.url.toString().equals(url)) {
                d.status = Status.Cancelled;
                toRemove.add(d);
            }
        }
        mDownloads.removeAll(toRemove);
    }

    private void startNextDownload() {
        if (hasQueuedDownloads() && mRunningDownloads < MAX_DOWNLOADS) {
            mHandler.postDelayed(this, 10);
        }
    }

    private boolean hasQueuedDownloads() {
        for (Download d : mDownloads) {
            if (d.status == Status.Queued) {
                return true;
            }
        }
        return false;
    }

    private Download getNextQueuedDownload() {
        for (Download d : mDownloads) {
            if (d.status == Status.Queued) {
                return d;
            }
        }
        return null;
    }

    private void addToDownloadsIfNotDuplicate(Download d) {
        boolean isDuplicate = false;
        for (Download download : mDownloads) {
            if (download.urlHash.equals(d.urlHash)) {
                isDuplicate = true;
                break;
            }
        }
        if (!isDuplicate) {
            mDownloads.add(d);
            if (mRunningDownloads < MAX_DOWNLOADS) {
                run();
            }
        }
    }

}
