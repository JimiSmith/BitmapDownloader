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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BitmapLoaderTask extends AsyncTask<String, Void, Bitmap> {
	private static final String TAG = BitmapLoaderTask.class.getCanonicalName();

	private WeakReference<ImageView> imageViewReference;
	private Context mContext;
	private BitmapLoadListener mListener;
	public String mUrl;
	private boolean mError;

	public interface BitmapLoadListener {
		public void notFound();

		public void loadBitmap(Bitmap b);

		public void onLoadError();

		public void onLoadCancelled();
	}

	public BitmapLoaderTask(ImageView imageView, BitmapLoadListener listener) {
		imageViewReference = new WeakReference<ImageView>(imageView);
		mContext = imageView.getContext().getApplicationContext();
		mListener = listener;
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

	@Override
	protected Bitmap doInBackground(String... params) {
		mUrl = params[0];
		String filename = md5(mUrl);
		Bitmap bitmap = null;
		if (isCancelled()) {
			return null;
		}
		if (filename != null) {
			try {
				FileInputStream local = mContext.openFileInput(filename);
				bitmap = BitmapFactory.decodeStream(local);
				if (bitmap == null) {
					Log.w(TAG, "The file specified is corrupt.");
					mContext.deleteFile(filename);
					mError = true;
					throw new FileNotFoundException("The file specified is corrupt.");
				}
			} catch (FileNotFoundException e) {
				Log.w(TAG, "Bitmap is not cached on disk. Redownloading.", e);
			}
		}
		return bitmap;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (bitmap == null && !mError && !isCancelled()) {
			mListener.notFound();
		} else {
			if (isCancelled()) {
				bitmap = null;
			}
			ImageView imageView = imageViewReference.get();

			if (imageView != null && !mError) {

				if (bitmap != null) {
					mListener.loadBitmap(bitmap);
				} else if (!isCancelled()){
					mListener.onLoadError();
				} else if (isCancelled()) {
					mListener.onLoadCancelled();
				}
			} else {
				mListener.onLoadError();
			}
		}
	}
}
