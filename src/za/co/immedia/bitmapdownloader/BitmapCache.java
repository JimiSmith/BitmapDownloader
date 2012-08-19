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
