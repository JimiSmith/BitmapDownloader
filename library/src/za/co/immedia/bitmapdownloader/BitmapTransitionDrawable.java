/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.immedia.bitmapdownloader;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.SystemClock;

/**
 * An extension of LayerDrawables that is intended to cross-fade between the
 * first and second layer. To start the transition, call
 * {@link #startTransition(int)}. To display just the first layer, call
 * {@link #resetTransition()}.
 * <p>
 * It can be defined in an XML file with the <code>&lt;transition></code>
 * element. Each Drawable in the transition is defined in a nested
 * <code>&lt;item></code>. For more
 * information, see the guide to <a
 * href="{@docRoot}. For more information, see the guide to <a href="{@docRoot}.
 * For more information, see the guide to <a href="{@docRoot}. For more
 * information, see the guide to <a href="{@docRoot}. For more information, see
 * the guide to <a href="{@docRoot}. For more information, see the guide to <a
 * href="{@docRoot}. For more information, see the guide to <a href="{@docRoot}.
 * For more information, see the guide to <a href="{@docRoot}
 * guide/topics/resources/drawable-resource.html">Drawable Resources</a>.
 * </p>
 * 
 * @attr ref android.R.styleable#LayerDrawableItem_left
 * @attr ref android.R.styleable#LayerDrawableItem_top
 * @attr ref android.R.styleable#LayerDrawableItem_right
 * @attr ref android.R.styleable#LayerDrawableItem_bottom
 * @attr ref android.R.styleable#LayerDrawableItem_drawable
 * @attr ref android.R.styleable#LayerDrawableItem_id
 * 
 */
public class BitmapTransitionDrawable extends LayerDrawable implements Drawable.Callback {

	public static interface BitmapTransitionCallback {
		public void onStarted();

		public void onEnded();
	}

	/**
	 * A transition is about to start.
	 */
	private static final int TRANSITION_STARTING = 0;

	/**
	 * The transition has started and the animation is in progress
	 */
	private static final int TRANSITION_RUNNING = 1;

	/**
	 * The transition has ended
	 */
	private static final int TRANSITION_ENDED = 2;

	/**
	 * No transition will be applied
	 */
	private static final int TRANSITION_NONE = 3;

	/**
	 * The current state of the transition. One of {@link #TRANSITION_STARTING},
	 * {@link #TRANSITION_RUNNING} and {@link #TRANSITION_NONE}
	 */
	private int mTransitionState = TRANSITION_NONE;

	private boolean mReverse;
	private long mStartTimeMillis;
	private int mFrom;
	private int mTo;
	private int mDuration;
	private int mOriginalDuration;
	private int mAlpha = 0;
	private boolean mCrossFade;
	private BitmapTransitionCallback mTransitionCallback;

	private Handler mHandler;

	/**
	 * Create a new transition drawable with the specified list of layers. At
	 * least 2 layers are required for this drawable to work properly.
	 */
	public BitmapTransitionDrawable(Drawable[] layers) {
		super(layers);
	}

	/**
	 * Begin the second layer on top of the first layer.
	 * 
	 * @param durationMillis
	 *          The length of the transition in milliseconds
	 */
	public void startTransition(int durationMillis) {
		mFrom = 0;
		mTo = 255;
		mAlpha = 0;
		mDuration = mOriginalDuration = durationMillis;
		mReverse = false;
		mTransitionState = TRANSITION_STARTING;
		invalidateSelf();
	}

	/**
	 * Show only the first layer.
	 */
	public void resetTransition() {
		mAlpha = 0;
		mTransitionState = TRANSITION_NONE;
		invalidateSelf();
	}

	/**
	 * Reverses the transition, picking up where the transition currently is. If
	 * the transition is not currently running, this will start the transition
	 * with the specified duration. If the transition is already running, the last
	 * known duration will be used.
	 * 
	 * @param duration
	 *          The duration to use if no transition is running.
	 */
	public void reverseTransition(int duration) {
		final long time = SystemClock.uptimeMillis();
		// Animation is over
		if (time - mStartTimeMillis > mDuration) {
			if (mTo == 0) {
				mFrom = 0;
				mTo = 255;
				mAlpha = 0;
				mReverse = false;
			} else {
				mFrom = 255;
				mTo = 0;
				mAlpha = 255;
				mReverse = true;
			}
			mDuration = mOriginalDuration = duration;
			mTransitionState = TRANSITION_STARTING;
			invalidateSelf();
			return;
		}

		mReverse = !mReverse;
		mFrom = mAlpha;
		mTo = mReverse ? 0 : 255;
		mDuration = (int) (mReverse ? time - mStartTimeMillis : mOriginalDuration - (time - mStartTimeMillis));
		mTransitionState = TRANSITION_STARTING;
		onStart();
	}

	@Override
	public void draw(Canvas canvas) {
		boolean done = true;

		switch (mTransitionState) {
		case TRANSITION_STARTING:
			mStartTimeMillis = SystemClock.uptimeMillis();
			done = false;
			mTransitionState = TRANSITION_RUNNING;
			break;

		case TRANSITION_RUNNING:
			if (mStartTimeMillis >= 0) {
				float normalized = (float) (SystemClock.uptimeMillis() - mStartTimeMillis) / mDuration;
				done = normalized >= 1.0f;
				normalized = Math.min(normalized, 1.0f);
				mAlpha = (int) (mFrom + (mTo - mFrom) * normalized);
			}
			break;
		}

		final int alpha = mAlpha;
		final boolean crossFade = mCrossFade;
		final Drawable[] array = { getDrawable(0), getDrawable(1) };

		if (done) {
			mTransitionState = TRANSITION_ENDED;
			// the setAlpha() calls below trigger invalidation and redraw. If we're
			// done, just draw
			// the appropriate drawable[s] and return
			if (!crossFade || alpha == 0) {
				array[0].draw(canvas);
			}
			if (alpha == 0xFF) {
				array[1].draw(canvas);
			}
			onEnd();
			return;
		}

		Drawable d;
		d = array[0];
		if (crossFade) {
			d.setAlpha(255 - alpha);
		}
		d.draw(canvas);
		if (crossFade) {
			d.setAlpha(0xFF);
		}

		if (alpha > 0) {
			d = array[1];
			d.setAlpha(alpha);
			d.draw(canvas);
			d.setAlpha(0xFF);
		}

		if (!done) {
			invalidateSelf();
		}
	}

	/**
	 * Enables or disables the cross fade of the drawables. When cross fade is
	 * disabled, the first drawable is always drawn opaque. With cross fade
	 * enabled, the first drawable is drawn with the opposite alpha of the second
	 * drawable. Cross fade is disabled by default.
	 * 
	 * @param enabled
	 *          True to enable cross fading, false otherwise.
	 */
	public void setCrossFadeEnabled(boolean enabled) {
		mCrossFade = enabled;
	}

	/**
	 * Indicates whether the cross fade is enabled for this transition.
	 * 
	 * @return True if cross fading is enabled, false otherwise.
	 */
	public boolean isCrossFadeEnabled() {
		return mCrossFade;
	}

	/**
	 * @return the {@link BitmapTransitionCallback}
	 */
	public BitmapTransitionCallback getTransitionCallback() {
		return mTransitionCallback;
	}

	/**
	 * @param transitionCallback
	 *          the {@link BitmapTransitionCallback} to set
	 */
	public void setTransitionCallback(BitmapTransitionCallback transitionCallback) {
		this.mTransitionCallback = transitionCallback;
	}
	
	private void onStart() {
		if (mTransitionCallback != null) {
			if (mHandler == null) {
				mHandler = new Handler();
			}
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					mTransitionCallback.onStarted();
				}
			});
		}
	}
	
	private void onEnd() {
		if (mTransitionCallback != null) {
			if (mHandler == null) {
				mHandler = new Handler();
			}
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					mTransitionCallback.onEnded();
				}
			});
		}
	}
}
