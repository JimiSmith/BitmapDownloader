/**
 * 
 */
package za.co.immedia.bitmapdownloader;

import android.graphics.Bitmap;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

/**
 * @author jimi
 *
 */
public class BitmapCache {
	private final LinkedHashMap<String, SoftReference<Bitmap> > mBitmapCache;
	private static final Integer CACHE_SIZE = 150;
//	static private final String TAG = BitmapCache.class.getCanonicalName();
	public BitmapCache() {
		mBitmapCache = new LinkedHashMap<String, SoftReference<Bitmap>>();
	}
	
	public void addBitmap(String url, Bitmap b) {
		if(mBitmapCache.size() > CACHE_SIZE) {
			mBitmapCache.remove(mBitmapCache.keySet().toArray()[0]);
		}
		mBitmapCache.put(url, new SoftReference<Bitmap>(b));
	}
	
	public Bitmap getBitmap(String url) {
		SoftReference<Bitmap> ref = mBitmapCache.get(url);
		if(ref != null) {
			Bitmap b = ref.get();
			if (b == null || b.isRecycled()) {
				mBitmapCache.remove(url);
				return null;
			}
			return ref.get();
		}
		return null;
	}
}
