/*
 * Copyright (c) 2012, James Smith
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package za.co.smith.BitmapDownloader;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import za.co.smith.BitmapDownloader.Manager.Download;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author James Smith
 */

public class BitmapDownloaderTask extends AsyncTask<Download, Float, Boolean> {
    private static final String TAG = BitmapDownloaderTask.class.getCanonicalName();
    public Download mDownload;
    public String mUrl;
    private Context mContext;
    private final BitmapDownloadListener mListener;
    private HttpGet mGetRequest;

    public interface BitmapDownloadListener {
        public void onComplete();

        public void onError();
    }

    public BitmapDownloaderTask(BitmapDownloadListener listener) {
        mListener = listener;
    }

    @Override
    protected Boolean doInBackground(Download... params) {
        mDownload = params[0];
        mUrl = mDownload.url.toString();
        mContext = mDownload.context;
        Boolean finished = false;
        try {
            finished = downloadBitmap();
        } catch (Exception e) {
            Log.w(TAG, "Error downloading bitmap", e);
        }
        return finished;
    }

    //for 2.2 where onCancelled(Object obj) is not implemented
    @Override
    protected void onCancelled() {
        onCancelled(false);
    }

    @Override
    protected void onCancelled(Boolean done) {
        mContext = null;
        Log.w(TAG, "onCancelled(Boolean):  " + done);
        //if the task is cancelled, abort the image request
        if (mGetRequest != null) {
            Log.w(TAG, "Aborting get request for:  " + mDownload);
            mGetRequest.abort();
            mGetRequest = null;
        }
    }

    @Override
    protected void onProgressUpdate(Float... values) {
    }

    @Override
    protected void onPostExecute(Boolean done) {
        mContext = null;
        if (isCancelled()) {
            done = false;
        }

        if (done) {
            mListener.onComplete();
        } else {
            mListener.onError();
        }
    }

    private int resolveUrl() {
        HttpHead headRequest = new HttpHead(mUrl);
        AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
        int statusCode = HttpStatus.SC_OK;
        try {
            HttpResponse response = client.execute(headRequest);
            statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_TEMPORARY_REDIRECT || statusCode == HttpStatus.SC_MOVED_PERMANENTLY ||
                    statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                mUrl = response.getFirstHeader("Location").getValue();
                return resolveUrl();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.close();
        }
        return statusCode;
    }

    private Boolean downloadBitmap() {
        if (isCancelled()) {
            return false;
        }
        String filename = mDownload.urlHash;
        Log.i(TAG, "filename: " + filename + ",url: " + mUrl);
        Boolean finished = true;
        AndroidHttpClient client = AndroidHttpClient.newInstance("Android");

        try {
            mGetRequest = new HttpGet(mUrl);
            HttpResponse response = client.execute(mGetRequest);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_TEMPORARY_REDIRECT || statusCode == HttpStatus.SC_MOVED_PERMANENTLY ||
                    statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                statusCode = resolveUrl();

                if (statusCode == HttpStatus.SC_OK) {
                    mGetRequest = new HttpGet(mUrl);
                    response = client.execute(mGetRequest);
                    statusCode = response.getStatusLine().getStatusCode();
                }
            }

            if (isCancelled()) {
                Log.i(TAG, "Download of " + mUrl + " was cancelled");
                finished = false;
            } else if (statusCode != HttpStatus.SC_OK) {
                Log.w(TAG, "Error " + statusCode + " while retrieving bitmap from " + mUrl);
                finished = false;
            } else {
                if (isCancelled()) {
                    return false;
                }
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream inputStream = null;
                    try {
                        inputStream = entity.getContent();
                        if (isCancelled()) {
                            return false;
                        }
                        BufferedOutputStream fos = new BufferedOutputStream(mContext.openFileOutput(filename, Context.MODE_PRIVATE));

                        byte[] buffer = new byte[1024];
                        int len = 0;
                        long contentLength = entity.getContentLength();
                        long contentDownloaded = 0;
                        while (!isCancelled() && (len = inputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                            contentDownloaded += len;
                            publishProgress((float) contentDownloaded / (float) contentLength);
                        }
                        fos.flush();
                        fos.close();
                        publishProgress(1.0f);
                        if (isCancelled()) {
                            return false;
                        }
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        entity.consumeContent();
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            finished = false;
            Log.w(TAG, "Error while retrieving bitmap from " + mDownload, e);
        } catch (FileNotFoundException e) {
            mGetRequest.abort();
            finished = false;
            Log.w(TAG, "Error while retrieving bitmap from " + mDownload, e);
        } catch (IOException e) {
            mGetRequest.abort();
            finished = false;
            Log.w(TAG, "Error while retrieving bitmap from " + mDownload, e);
        } finally {
            mGetRequest = null;
            client.close();
        }
        return finished;
    }
}