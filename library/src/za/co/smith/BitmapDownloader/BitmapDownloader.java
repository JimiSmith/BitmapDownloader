package za.co.smith.BitmapDownloader;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;
import za.co.smith.BitmapDownloader.BitmapLoaderTask.BitmapLoadListener;
import za.co.smith.BitmapDownloader.Manager.ManagerDelegate;
import za.co.smith.bitmapdownloader.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class BitmapDownloader implements ManagerDelegate {

    private static String TAG = BitmapDownloader.class.getCanonicalName();
    private static BitmapDownloader sInstance;

    public static class Options {
        private static String ERROR_RESOURCE = "errorResource";
        private static String PLACEHOLDER_RESOURCE = "placeHolderResource";
        private static String MAX_WIDTH = "maxWidth";
        private static String MAX_HEIGHT = "maxHeight";
        private static String MEM_CACHE_SIZE = "memCacheSize";
        private static String USE_EXISTING = "useExisting";

        private HashMap<String, Object> mOptions = new HashMap<String, Object>();

        @SuppressWarnings("unchecked")
        private <T> T getOptionWithDefault(String key, T defaultValue) {
            return (T) (mOptions.containsKey(key) ? mOptions.get(key) : defaultValue);
        }

        public Integer getErrorResource() {
            return getOptionWithDefault(ERROR_RESOURCE, -1);
        }

        public Integer getPlaceholderResource() {
            return getOptionWithDefault(PLACEHOLDER_RESOURCE, -1);
        }

        public Integer getMaxWidth() {
            return getOptionWithDefault(MAX_WIDTH, 2048);
        }

        public Integer getMaxHeight() {
            return getOptionWithDefault(MAX_HEIGHT, 2048);
        }

        public Integer getMemoryCacheSize() {
            return getOptionWithDefault(MEM_CACHE_SIZE, 4 * 1024 * 1024);
        }

        public Boolean canUseExistingImageAsThumb() {
            return getOptionWithDefault(USE_EXISTING, false);
        }

        public Options setErrorResource(int resource) {
            mOptions.put(ERROR_RESOURCE, resource);
            return this;
        }

        public Options setPlaceholderResource(int resource) {
            mOptions.put(PLACEHOLDER_RESOURCE, resource);
            return this;
        }

        public Options setMaxWidth(int maxWidth) {
            mOptions.put(MAX_WIDTH, maxWidth);
            return this;
        }

        public Options setMaxHeight(int maxHeight) {
            mOptions.put(MAX_HEIGHT, maxHeight);
            return this;
        }

        public Options setMemoryCacheSize(int memSize) {
            mOptions.put(MEM_CACHE_SIZE, memSize);
            return this;
        }

        public Options setUseExistingImageAsThumb(boolean useExistingImageAsThumb) {
            mOptions.put(USE_EXISTING, useExistingImageAsThumb);
            return this;
        }

        public Options mergedCopy(Options o) {
            Options options = new Options();
            options.mOptions = new HashMap<String, Object>(mOptions);
            options.mOptions.putAll(o.mOptions);
            return options;
        }
    }

    private Manager mManager;
    private HashMap<String, ArrayList<WeakReference<ImageView>>> mDownloads;
    private HashMap<String, Options> mOptions;
    private Options mDefaultOptions;
    private BitmapCache mCache;

    public static BitmapDownloader getInstance() {
        if (sInstance == null) {
            sInstance = new BitmapDownloader();
        }
        return sInstance;
    }

    private BitmapDownloader() {
        mManager = new Manager(this);
        mDownloads = new HashMap<String, ArrayList<WeakReference<ImageView>>>();
        mDefaultOptions = new Options();
        mOptions = new HashMap<String, BitmapDownloader.Options>();
    }

    public void setDefaultOptions(Options options) {
        mDefaultOptions = options;
    }

    public void download(String url, ImageView imageView) {
        download(url, imageView, mDefaultOptions);
    }

    public void download(String url, ImageView imageView, Options o) {
        Options options = mDefaultOptions.mergedCopy(o);
        if (imageView.getTag(R.id.bmd__image_downloader) != null) {
            mManager.cancelDownload((String) imageView.getTag(R.id.bmd__image_downloader));
        }
        if (url == null) {
            if (!options.canUseExistingImageAsThumb() && options.getErrorResource() != -1) {
                imageView.setImageResource(options.getErrorResource());
            }
            return;
        }
        if (mCache == null) {
            mCache = new BitmapCache(mDefaultOptions.getMemoryCacheSize());
        }
        imageView.setTag(R.id.bmd__image_downloader, url);
        Bitmap bitmap = mCache.getBitmap(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            mOptions.put(url, options);
            loadFileFromDisk(url, imageView);
        }
    }

    @Override
    public void downloadComplete(final String url) {
        ArrayList<WeakReference<ImageView>> imageViewRefs = mDownloads.get(url);
        mDownloads.remove(url);
        if (imageViewRefs != null) {
            for (WeakReference<ImageView> imageViewRef : imageViewRefs) {

                if (imageViewRef != null) {
                    final ImageView imageView = imageViewRef.get();
                    if (imageView != null && imageView.getTag(R.id.bmd__image_downloader).equals(url)) {
                        loadFileFromDisk(url, imageView);
                    }
                }
            }
        }
    }

    @Override
    public void downloadFailed(String url) {
        ArrayList<WeakReference<ImageView>> imageViewRefs = mDownloads.get(url);
        if (imageViewRefs != null) {
            Options options = mOptions.get(url);
            for (WeakReference<ImageView> imageViewRef : imageViewRefs) {

                if (imageViewRef != null) {
                    final ImageView imageView = imageViewRef.get();
                    if (imageView != null && imageView.getTag(R.id.bmd__image_downloader).equals(url)) {
                        if (options.getPlaceholderResource() != -1) {
                            imageView.setImageResource(options.getErrorResource());
                        } else {
                            imageView.setImageDrawable(null);
                        }
                    }
                }
            }
        }
        mDownloads.remove(url);
    }

    @SuppressLint("NewApi")
    private void loadFileFromDisk(final String url, final ImageView imageView) {
        Options options = mOptions.get(url);
        if (!options.canUseExistingImageAsThumb()) {
            if (options.getPlaceholderResource() != -1) {
                imageView.setImageResource(options.getPlaceholderResource());
            } else {
                imageView.setImageDrawable(null);
            }
        }
        BitmapLoaderTask loaderTask = new BitmapLoaderTask(imageView, options, new BitmapLoadListener() {

            @Override
            public void notFound() {
                ArrayList<WeakReference<ImageView>> imageViewRefs = mDownloads.get(url);
                if (imageViewRefs == null) {
                    imageViewRefs = new ArrayList<WeakReference<ImageView>>();
                }
                imageViewRefs.add(new WeakReference<ImageView>(imageView));
                if (mDownloads.put(url, imageViewRefs) == null) {
                    mManager.addDownload(url, imageView.getContext());
                }
            }

            @Override
            public void loadError() {
                Options options = mOptions.get(url);
                if (options.getPlaceholderResource() != -1) {
                    imageView.setImageResource(options.getErrorResource());
                } else {
                    imageView.setImageDrawable(null);
                }
            }

            @Override
            public void loadBitmap(Bitmap b) {
                mCache.addBitmap(url, b);
                if (imageView.getTag(R.id.bmd__image_downloader).equals(url)) {
                    imageView.setImageBitmap(b);
                }
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            loaderTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
        } else {
            loaderTask.execute(url);
        }
    }

}
