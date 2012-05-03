/**
 *
 */
package za.co.immedia.bitmapdownloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author James Smith
 */

public class BitmapDownloaderTask extends AsyncTask<String, Void, Boolean> {
	private static final String TAG = BitmapDownloaderTask.class.getCanonicalName();
	public String mUrl;
	private final WeakReference<ImageView> imageViewReference;
	private final Context mContext;
	private final BitmapDownloadListener mListener;
	private HttpGet mGetRequest;

	public interface BitmapDownloadListener {
		public void onComplete();

		public void onError();

		public void onCancel();
	}

	public BitmapDownloaderTask(ImageView imageView, BitmapDownloadListener listener) {
		imageViewReference = new WeakReference<ImageView>(imageView);
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

	public String md5(String s) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(s.getBytes());
			byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuilder hexString = new StringBuilder();
			for (byte aMessageDigest : messageDigest) {
				hexString.append(Integer.toHexString(0xFF & aMessageDigest));
			}
			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
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
		String filename = md5(mUrl); //get the filename before we follow any redirects. very important
		Boolean finished = true;
		AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
		mGetRequest = new HttpGet(mUrl);

		try {
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
		} catch (Exception e) {
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