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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class BitmapDownloader {

	private static final String TAG = BitmapDownloader.class.getCanonicalName();

	public static final int DOWNLOAD_TAG = R.id.bmd__image_downloader;

	private final BitmapCache mBitmapCache = new BitmapCache();
	private ArrayList<Download> mQueuedDownloads;
	private ArrayList<Download> mRunningDownloads;
	private HashMap<String, ArrayList<Download>> mDuplicateDownloads;
	private int mMaxDownloads;
	private Drawable mErrorDrawable;
	private Drawable mInProgressDrawable;
	private boolean mAnimateImageAppearance = false;
	private boolean mAnimateImageAppearanceAfterDownload = true;

	public static enum AnimateAppearance {
		ANIMATE_ALWAYS, ANIMATE_AFTER_DOWNLOAD, ANIMATE_NEVER
	}

	public BitmapDownloader() {
		setup(5);
	}

	public BitmapDownloader(int maxDownloads) {
		setup(maxDownloads);
	}

	private void setup(int maxDownloads) {
		mQueuedDownloads = new ArrayList<Download>();
		mRunningDownloads = new ArrayList<Download>();
		mMaxDownloads = maxDownloads;
		mDuplicateDownloads = new HashMap<String, ArrayList<Download>>();
	}

	public void setErrorDrawable(Drawable errorDrawable) {
		this.mErrorDrawable = errorDrawable;
	}

	public void setInProgressDrawable(Drawable inProgressDrawable) {
		this.mInProgressDrawable = inProgressDrawable;
	}

	public void setAnimateImageAppearance(AnimateAppearance animate) {
		switch (animate) {
			case ANIMATE_ALWAYS: {
				mAnimateImageAppearance = true;
				mAnimateImageAppearanceAfterDownload = true;
				break;
			}
			case ANIMATE_AFTER_DOWNLOAD: {
				mAnimateImageAppearance = false;
				mAnimateImageAppearanceAfterDownload = true;
				break;
			}
			case ANIMATE_NEVER: {
				mAnimateImageAppearance = false;
				mAnimateImageAppearanceAfterDownload = false;
				break;
			}

			default:
				break;
		}
	}

	public void download(String url, ImageView imageView) {
		Download d = new Download(url, imageView);
		d.loadImage();
	}

	public void cancelAllDownloads() {
		mQueuedDownloads.clear();
		for (Download download : mRunningDownloads) {
			BitmapDownloaderTask task = download.getBitmapDownloaderTask();
			if (task != null) {
				task.cancel(true);
			}
		}
		mRunningDownloads.clear();
	}

	public class Download implements BitmapDownloaderTask.BitmapDownloadListener, BitmapLoaderTask.BitmapLoadListener {
		private String mUrl;
		private WeakReference<ImageView> mImageViewRef;
		private BitmapDownloaderTask mBitmapDownloaderTask;
		private BitmapLoaderTask mBitmapLoaderTask;
		private boolean mIsCancelled;
		private boolean mWasDownloaded = false;

		public Download(String url, ImageView imageView) {
			this.mUrl = url;
			this.mImageViewRef = new WeakReference<ImageView>(imageView);
			mIsCancelled = false;
			imageView.setImageDrawable(null);
		}

		public BitmapDownloaderTask getBitmapDownloaderTask() {
			return mBitmapDownloaderTask;
		}

		public ImageView getImageView() {
			return mImageViewRef.get();
		}

		public String getUrl() {
			return mUrl;
		}

		public void loadImage() {
			ImageView imageView = mImageViewRef.get();
			if (imageView != null) {
				Bitmap cachedBitmap = mBitmapCache.getBitmap(mUrl);
				// find the old download, cancel it and set this download as the current
				// download for the imageview
				Download oldDownload = (Download) imageView.getTag(DOWNLOAD_TAG);
				if (oldDownload != null) {
					oldDownload.cancel();
				}
				if (cachedBitmap != null) {
					mWasDownloaded = false;
					BitmapDrawable bm = new BitmapDrawable(imageView.getResources(), cachedBitmap);
					loadDrawable(bm);
					imageView.setTag(DOWNLOAD_TAG, null);
				} else {
					imageView.setTag(DOWNLOAD_TAG, this);
					loadFromDisk(imageView);
				}
			}
		}

		public void doDownload() {
			if (mIsCancelled) { // if the download has been cancelled, do not download this image, but start the next one
				if (!mQueuedDownloads.isEmpty()) {
					Download d = mQueuedDownloads.remove(0);
					d.doDownload();
				}
				return;
			}
			ImageView imageView = mImageViewRef.get();
			if (imageView != null && imageView.getTag(DOWNLOAD_TAG) == this) { // if the ImageView hasn't been GC'd yet
				mBitmapDownloaderTask = new BitmapDownloaderTask(imageView, this);
				mBitmapDownloaderTask.execute(mUrl);
				Log.d(TAG, "doDownload: " + mUrl);
				mRunningDownloads.add(this);
			}
		}

		private boolean isBeingDownloaded() {
			for (Download download : mRunningDownloads) {
				if (download == null) {
					continue;
				}
				ImageView otherImageView = download.getImageView();
				ImageView thisImageView = getImageView();
				if (thisImageView == null || otherImageView == null) {
					continue;
				}
				if (otherImageView.equals(thisImageView) && download.getUrl().equals(mUrl)) {
					return true;
				}
			}
			return false;
		}

		private void loadFromDisk(ImageView imageView) {
			if (imageView != null && !mIsCancelled) {
				mBitmapLoaderTask = new BitmapLoaderTask(imageView, this);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					mBitmapLoaderTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mUrl);
				} else {
					mBitmapLoaderTask.execute(mUrl);
				}
			}
		}

		private void cancel() {
			Log.d(TAG, "cancel requested for: " + mUrl);
			mIsCancelled = true;
			if (mQueuedDownloads.contains(this)) {
				mQueuedDownloads.remove(this);
			}
			if (mBitmapDownloaderTask != null) mBitmapDownloaderTask.cancel(true);
			if (mBitmapLoaderTask != null) mBitmapLoaderTask.cancel(true);
		}

		private int indexOfDownloadWithDifferentURL() {
			for (Download download : mRunningDownloads) {
				if (download == null) {
					continue;
				}
				ImageView otherImageView = download.getImageView();
				ImageView thisImageView = getImageView();
				if (thisImageView == null || otherImageView == null) {
					continue;
				}
				if (otherImageView.equals(thisImageView) && !download.getUrl().equals(mUrl)) {
					return mRunningDownloads.indexOf(download);
				}
			}
			return -1;
		}

		private boolean isQueuedForDownload() {
			for (Download download : mQueuedDownloads) {
				if (download == null) {
					continue;
				}
				ImageView otherImageView = download.getImageView();
				ImageView thisImageView = getImageView();
				if (thisImageView == null || otherImageView == null) {
					continue;
				}
				if (otherImageView.equals(thisImageView) && download.getUrl().equals(mUrl)) {
					return true;
				}
			}
			return false;
		}

		private int indexOfQueuedDownloadWithDifferentURL() {
			for (Download download : mQueuedDownloads) {
				if (download == null) {
					continue;
				}
				ImageView otherImageView = download.getImageView();
				ImageView thisImageView = getImageView();
				if (thisImageView == null || otherImageView == null) {
					continue;
				}
				if (otherImageView.equals(thisImageView) && !download.getUrl().equals(mUrl)) {
					return mQueuedDownloads.indexOf(download);
				}
			}
			return -1;
		}

		private boolean isAnotherQueuedOrRunningWithSameUrl() {
			for (Download download : mQueuedDownloads) {
				if (download == null) {
					continue;
				}
				if (download.getUrl().equals(mUrl)) {
					return true;
				}
			}
			for (Download download : mRunningDownloads) {
				if (download == null) {
					continue;
				}
				if (download.getUrl().equals(mUrl)) {
					return true;
				}
			}
			return false;
		}

		private void loadDrawable(Drawable d) {
			loadDrawable(d, true);
		}

		private void loadDrawable(Drawable d, boolean animate) {
			Log.d(TAG, "loadDrawable: " + d);
			ImageView imageView = getImageView();
			if (imageView != null) {
				if (animate && (mAnimateImageAppearance || (mAnimateImageAppearanceAfterDownload && mWasDownloaded))) {
					Drawable current = imageView.getDrawable();
					if (current == null) {
						current = new ColorDrawable(Color.TRANSPARENT);
					}
					Drawable[] layers = {current, d};
					TransitionDrawable drawable = new TransitionDrawable(layers);
					imageView.setImageDrawable(drawable);
					drawable.setCrossFadeEnabled(true); //fade out the old image
					drawable.startTransition(200);
				} else {
					imageView.setImageDrawable(d);
				}
			}
		}

		// called when the download has completed
		@Override
		public void onComplete() {
			Log.d(TAG, "onComplete: " + mUrl);

			mRunningDownloads.remove(this);
			mWasDownloaded = true;

			ImageView imageView = mImageViewRef.get();
			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) { // if the imageView still belongs to us, load the image into it
				loadFromDisk(getImageView());
			}

			ArrayList<Download> duplicates = mDuplicateDownloads.get(mUrl);
			if (duplicates != null) {
				for (Download dup : duplicates) {
					Log.d(TAG, "onComplete: " + dup.mUrl);
					// load the image.
					if (dup.getImageView().getTag(DOWNLOAD_TAG) == dup) {
						dup.loadFromDisk(dup.getImageView());
					}
				}
				mDuplicateDownloads.remove(mUrl);
			}

			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				d.doDownload();
			}
		}

		// called if there is an error with the download
		@Override
		public void onError() {
			Log.d(TAG, "onError: " + mUrl);
			mRunningDownloads.remove(this);
			ImageView imageView = mImageViewRef.get();
			mWasDownloaded = true;
			if (imageView != null && mErrorDrawable != null) {
				loadDrawable(mErrorDrawable);
			}

			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) {
				imageView.setTag(DOWNLOAD_TAG, null);
			}
			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				d.doDownload();
			}
		}

		// called if the download is cancelled
		@Override
		public void onCancel() {
			mIsCancelled = true;
			Log.d(TAG, "onCancel: " + mUrl);
			mRunningDownloads.remove(this);

			ImageView imageView = mImageViewRef.get();
			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) {
				imageView.setTag(DOWNLOAD_TAG, null);
			}
			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				Log.d(TAG, "starting DL of: " + d.getUrl());
				d.doDownload();
			}
		}


		// called if the file is not found on the file system
		@Override
		public void notFound() {
			Log.d(TAG, "notFound: " + mUrl);
			if (mIsCancelled) return;
			ImageView imageView = getImageView();

			if (imageView == null || this != imageView.getTag(DOWNLOAD_TAG)) return;

			if (mInProgressDrawable != null) {
				loadDrawable(mInProgressDrawable, false);
			}
			if (isAnotherQueuedOrRunningWithSameUrl()) {
				if (mDuplicateDownloads.containsKey(mUrl)) {
					ArrayList<Download> arr = mDuplicateDownloads.get(mUrl);
					arr.add(this);
					mDuplicateDownloads.put(mUrl, arr);
				} else {
					ArrayList<Download> arr = new ArrayList<Download>();
					arr.add(this);
					mDuplicateDownloads.put(mUrl, arr);
				}
			} else {
				// check if this imageView is being used with a different URL, if so
				// cancel the other one.
				int queuedIndex = indexOfQueuedDownloadWithDifferentURL();
				int downloadIndex = indexOfDownloadWithDifferentURL();
				while (queuedIndex != -1) {
					mQueuedDownloads.remove(queuedIndex);
					Log.d(TAG, "notFound(Removing): " + mUrl);
					queuedIndex = indexOfQueuedDownloadWithDifferentURL();
				}
				if (downloadIndex != -1) {
					Download runningDownload = mRunningDownloads.get(downloadIndex);
					BitmapDownloaderTask downloadTask = runningDownload.getBitmapDownloaderTask();
					if (downloadTask != null) {
						downloadTask.cancel(true);
						Log.d(TAG, "notFound(Cancelling): " + mUrl);
						Log.d(TAG, "imageView was downloading: " + runningDownload.getUrl() + "; should be downloading: " + mUrl);
						Log.d(TAG, "cancelled: " + runningDownload.getUrl());
					}
				}

				if (!(isBeingDownloaded() || isQueuedForDownload())) {
					if (mRunningDownloads.size() >= mMaxDownloads) {
						Log.d(TAG, "notFound(Queuing): " + mUrl);
						mQueuedDownloads.add(this);
					} else {
						Log.d(TAG, "notFound(Downloading): " + mUrl);
						doDownload();
					}
				}
			}
		}

		@Override
		public void loadBitmap(Bitmap b) {
			Log.d(TAG, "loadBitmap: " + mUrl);
			mBitmapCache.addBitmap(mUrl, b);
			ImageView imageView = getImageView();
			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) {
				BitmapDrawable bm = new BitmapDrawable(imageView.getResources(), b);
				loadDrawable(bm);
				imageView.setTag(DOWNLOAD_TAG, null);
			}
			mWasDownloaded = false;
		}

		@Override
		public void onLoadError() {
			Log.d(TAG, "onLoadError: " + mUrl);
			ImageView imageView = getImageView();
			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) {
				imageView.setTag(DOWNLOAD_TAG, null);
			}
		}

		@Override
		public void onLoadCancelled() {
			Log.d(TAG, "onLoadCancelled: " + mUrl);
			ImageView imageView = getImageView();
			if (imageView != null && this == imageView.getTag(DOWNLOAD_TAG)) {
				imageView.setTag(DOWNLOAD_TAG, null);
			}
		}
	}

}
