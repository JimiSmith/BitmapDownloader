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

package za.co.immedia.bitmapdownloader;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

/**
 * @author James Smith
 */

public class BitmapDownloaderTask extends AsyncTask<String, Void, Boolean> {
	private static final String TAG = BitmapDownloaderTask.class.getCanonicalName();
	public String mUrl;
	private final Context mContext;
	private final BitmapDownloadListener mListener;
	private HttpGet mGetRequest;

	public interface BitmapDownloadListener {
		public void onComplete();

		public void onError();

		public void onCancel();
	}

	public BitmapDownloaderTask(ImageView imageView, BitmapDownloadListener listener) {
		mContext = imageView.getContext().getApplicationContext();
		mListener = listener;
	}

	@Override
	protected Boolean doInBackground(String... params) {
		mUrl = params[0];
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
		Log.w(TAG, "onCancelled(Boolean):  " + done);
		mListener.onCancel();
		//if the task is cancelled, abort the image request
		if (mGetRequest != null) {
			Log.w(TAG, "Aborting get request for:  " + mUrl);
			mGetRequest.abort();
			mGetRequest = null;
		}
	}

	@Override
	// Once the image is downloaded, associates it to the imageView
	protected void onPostExecute(Boolean done) {
		if (isCancelled()) {
			done = false;
		}
		Log.w(TAG, "onPostExecute:  " + done);

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
		String filename = Utilities.md5(mUrl); //get the filename before we follow any redirects. very important
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
						FileOutputStream fos = mContext.openFileOutput(filename, Context.MODE_PRIVATE);

						byte[] buffer = new byte[1024];
						int len = 0;
						while (!isCancelled() && (len = inputStream.read(buffer)) > 0) {
							fos.write(buffer, 0, len);
						}
						fos.close();
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
			Log.w(TAG, "Error while retrieving bitmap from " + mUrl, e);
		} catch (FileNotFoundException e) {
			mGetRequest.abort();
			finished = false;
			Log.w(TAG, "Error while retrieving bitmap from " + mUrl, e);
		} catch (IOException e) {
			mGetRequest.abort();
			finished = false;
			Log.w(TAG, "Error while retrieving bitmap from " + mUrl, e);
		} finally {
			mGetRequest = null;
			client.close();
		}
		return finished;
	}
}