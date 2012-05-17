package za.co.immedia.bitmapdownloader;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
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
		mInProgressDrawable = new ColorDrawable(Color.TRANSPARENT);
		mDuplicateDownloads = new HashMap<String, ArrayList<Download>>();
		mErrorDrawable = new ColorDrawable(Color.TRANSPARENT);
	}

	public void setErrorDrawable(Drawable errorDrawable) {
		this.mErrorDrawable = errorDrawable;
	}

	public void setInProgressDrawable(Drawable inProgressDrawable) {
		this.mInProgressDrawable = inProgressDrawable;
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
		private WeakReference<BitmapDownloaderTask> bitmapDownloaderTaskReference;
		private WeakReference<BitmapLoaderTask> bitmapLoaderTaskReference;

		public Download(String url, ImageView imageView) {
			this.mUrl = url;
			this.mImageViewRef = new WeakReference<ImageView>(imageView);
			DownloadedDrawable d = new DownloadedDrawable();
			d.setInProgressDrawable(mInProgressDrawable);
			d.setErrorDrawable(mErrorDrawable);
			imageView.setImageDrawable(d);
		}

		public BitmapDownloaderTask getBitmapDownloaderTask() {
			if (bitmapDownloaderTaskReference == null) {
				return null;
			} else {
				return bitmapDownloaderTaskReference.get();
			}
		}

		public BitmapLoaderTask getBitmapLoaderTask() {
			if (bitmapLoaderTaskReference == null) {
				return null;
			} else {
				return bitmapLoaderTaskReference.get();
			}
		}

		public ImageView getImageView() {
			return mImageViewRef.get();
		}

		public String getUrl() {
			return mUrl;
		}

		public void loadImage() {
			Bitmap cachedBitmap = mBitmapCache.getBitmap(mUrl);
			ImageView imageView = mImageViewRef.get();
			if (imageView != null) {
				Download oldDownload = (Download) imageView.getTag(DOWNLOAD_TAG);
				if (oldDownload != null) {
					if (mQueuedDownloads.contains(oldDownload)) {
						mQueuedDownloads.remove(oldDownload);
					}
					BitmapDownloaderTask downloadTask = oldDownload.getBitmapDownloaderTask();
					if (downloadTask != null) {
						downloadTask.cancel(true);
					}
				}
				imageView.setTag(DOWNLOAD_TAG, this);
				if (cachedBitmap != null) {
					imageView.setImageBitmap(cachedBitmap);
				} else { //if the ImageView hasn't been GC'd yet

					try {
						((DownloadedDrawable) imageView.getDrawable()).setShownBitmap(DownloadedDrawable.ShownDrawable.IN_PROGRESS);
					} catch (ClassCastException e) {
						DownloadedDrawable d = new DownloadedDrawable();
						d.setInProgressDrawable(mInProgressDrawable);
						d.setErrorDrawable(mErrorDrawable);
						imageView.setImageDrawable(d);
						((DownloadedDrawable) imageView.getDrawable()).setShownBitmap(DownloadedDrawable.ShownDrawable.IN_PROGRESS);
					}

					BitmapLoaderTask bitmapLoaderTask = new BitmapLoaderTask(imageView, this);
					bitmapLoaderTaskReference = new WeakReference<BitmapLoaderTask>(bitmapLoaderTask);
					bitmapLoaderTask.execute(mUrl);
					Log.d(TAG, "loadImage: " + mUrl);
				}
			}
		}

		public void doDownload() {
			ImageView imageView = mImageViewRef.get();
			if (imageView != null) { //if the ImageView hasn't been GC'd yet
				BitmapDownloaderTask task = new BitmapDownloaderTask(imageView, this);
				bitmapDownloaderTaskReference = new WeakReference<BitmapDownloaderTask>(task);
				task.execute(mUrl);
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

		@Override
		public void onComplete() {
			mRunningDownloads.remove(this);
			Log.d(TAG, "onComplete: " + mUrl);
			//load the image.

			loadImage();

			ArrayList<Download> duplicates = mDuplicateDownloads.get(mUrl);
			if (duplicates != null) {
				for (Download dup : duplicates) {
					Log.d(TAG, "onComplete: " + dup.mUrl);
					//load the image.
					dup.loadImage();
				}
				mDuplicateDownloads.remove(mUrl);
			}

			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				d.doDownload();
			}
		}

		@Override
		public void onError() {
			Log.d(TAG, "onError: " + mUrl);
			mRunningDownloads.remove(this);
			ImageView imageView = mImageViewRef.get();
			if (imageView != null) {
				try {
					((DownloadedDrawable) imageView.getDrawable()).setShownBitmap(DownloadedDrawable.ShownDrawable.ERROR);
				} catch (ClassCastException e) {
					DownloadedDrawable d = new DownloadedDrawable();
					d.setInProgressDrawable(mInProgressDrawable);
					d.setErrorDrawable(mErrorDrawable);
					imageView.setImageDrawable(d);
					((DownloadedDrawable) imageView.getDrawable()).setShownBitmap(DownloadedDrawable.ShownDrawable.ERROR);
				}
			}
			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				d.doDownload();
			}
			bitmapDownloaderTaskReference.clear();
			bitmapLoaderTaskReference.clear();
		}

		@Override
		public void onCancel() {
			Log.d(TAG, "onCancel: " + mUrl);
			mRunningDownloads.remove(this);
			if (!mQueuedDownloads.isEmpty()) {
				Download d = mQueuedDownloads.remove(0);
				d.doDownload();
			}
			bitmapDownloaderTaskReference.clear();
			bitmapLoaderTaskReference.clear();
		}

		@Override
		public void notFound() {
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
				//check if this imageView is being used with a different URL, if so cancel the other one.
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
		public void addToCache(Bitmap b) {
			mBitmapCache.addBitmap(mUrl, b);
		}

		@Override
		public void onLoaded() {
			Log.d(TAG, "onLoaded: " + mUrl);
			bitmapLoaderTaskReference.clear();
		}

		@Override
		public void onLoadError() {
			Log.d(TAG, "onLoadError: " + mUrl);
			bitmapLoaderTaskReference.clear();
		}
	}

	public static class DownloadedDrawable extends LevelListDrawable {

		public static enum ShownDrawable {
			DEFAULT,
			IN_PROGRESS,
			COMPLETE,
			ERROR
		}

		public DownloadedDrawable() {
		}

		public void setInProgressDrawable(Drawable d) {
			addLevel(1, 1, d);
			int alpha = 255;
			try {
				alpha = (Integer) d.getClass().getMethod("getAlpha", null).invoke(d, null);
			} catch (Exception e) {
				Log.i(TAG, d.toString() + " does not support getAlpha");
			}
			setAlpha(alpha);
			invalidateSelf();
		}

		public void setCompleteDrawable(Drawable d) {
			addLevel(2, 2, d);
			int alpha = 255;
			try {
				alpha = (Integer) d.getClass().getMethod("getAlpha", null).invoke(d, null);
			} catch (Exception e) {
				Log.i(TAG, d.toString() + " does not support getAlpha");
			}
			setAlpha(alpha);
			invalidateSelf();
		}

		public void setErrorDrawable(Drawable d) {
			addLevel(3, 3, d);
			int alpha = 255;
			try {
				alpha = (Integer) d.getClass().getMethod("getAlpha", null).invoke(d, null);
			} catch (Exception e) {
				Log.i(TAG, d.toString() + " does not support getAlpha");
			}
			setAlpha(alpha);
			invalidateSelf();
		}

		public DownloadedDrawable setShownBitmap(ShownDrawable level) {
			setLevel(level.ordinal());
			return this;
		}
	}

}