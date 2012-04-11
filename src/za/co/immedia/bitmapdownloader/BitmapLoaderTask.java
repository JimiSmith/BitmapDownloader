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

	public interface BitmapLoadListener {
		public void onLoaded();

		public void notFound();

		public void addToCache(Bitmap b);

		public void onLoadError();
	}

	public BitmapLoaderTask(ImageView imageView, BitmapLoadListener listener) {
		imageViewReference = new WeakReference<ImageView>(imageView);
		mContext = imageView.getContext().getApplicationContext();
		mListener = listener;
	}

	@Override
	protected void onCancelled(Bitmap bitmap) {
		super.onCancelled(bitmap);
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
		if (isCancelled()) {
			return null;
		}
		String filename = md5(mUrl);
		Bitmap bitmap = null;
		if (filename != null) {
			try {
				FileInputStream local = mContext.openFileInput(filename);
				bitmap = BitmapFactory.decodeStream(local);
				if (bitmap == null) {
					Log.w(TAG, "The file specified is corrupt.");
					throw new FileNotFoundException("The file specified is corrupt.");
				}
			} catch (FileNotFoundException e) {
				Log.w(TAG, "Bitmap is not cached on disk. Redownloading.");
			}
		}
		return bitmap;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (bitmap == null) {
			mListener.notFound();
		} else {
			if (isCancelled()) {
				bitmap = null;
			}
			ImageView imageView = imageViewReference.get();

			if (imageView != null) {

				BitmapDownloader.Download download = (BitmapDownloader.Download) imageView.getTag(BitmapDownloader.DOWNLOAD_TAG);

				if(this == download.getBitmapLoaderTask() && bitmap != null) {
					imageView.setImageBitmap(bitmap);
					imageView.requestLayout();
					mListener.addToCache(bitmap);
					mListener.onLoaded();
				} else {
					mListener.onLoadError();
				}
			} else {
				mListener.onLoadError();
			}
		}
	}
}
