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
import java.io.IOException;
import java.lang.ref.WeakReference;

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

	/**
	 * Conservatively estimates inSampleSize. Given a required width and height,
	 * this method calculates an inSampleSize that will result in a bitmap that is
	 * approximately the size requested, but guaranteed to not be smaller than
	 * what is requested.
	 * 
	 * @param options
	 *          the {@link BitmapFactory.Options} obtained by decoding the image
	 *          with inJustDecodeBounds = true
	 * @param reqWidth
	 *          the required width
	 * @param reqHeight
	 *          the required height
	 * 
	 * @return the calculated inSampleSize
	 */
	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}
		}
		return inSampleSize;
	}

	@Override
	protected Bitmap doInBackground(String... params) {
		mUrl = params[0];
		if (mUrl == null) {
			return null;
		}
		String filename = Utilities.md5(mUrl);
		Bitmap bitmap = null;
		if (isCancelled()) {
			return null;
		}
		if (filename != null) {
			try {
				FileInputStream local = mContext.openFileInput(filename);
				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeFileDescriptor(local.getFD(), null, options);

				options.inSampleSize = calculateInSampleSize(options, 1024, 1024);
				options.inJustDecodeBounds = false;
				bitmap = BitmapFactory.decodeFileDescriptor(local.getFD(), null, options);
				if (bitmap == null) {
					Log.w(TAG, "The file specified is corrupt.");
					mContext.deleteFile(filename);
					mError = true;
					throw new FileNotFoundException("The file specified is corrupt.");
				}
			} catch (FileNotFoundException e) {
				Log.w(TAG, "Bitmap is not cached on disk. Redownloading.", e);
			} catch (IOException e) {
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
				} else if (!isCancelled()) {
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
