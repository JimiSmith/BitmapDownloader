BitmapDownloader
================

This library will download images from a remote URL, and load them into an imageview.

Currently an in memory cache is searched first, then an on disk cache, then finally the image will be retrieved from it's remote source.

If multiple imageviews are specified with the same URL, then the image will only be downloaded once, and loaded into all imageviews.

Example
-------
	BitmapDownloader bm = new BitmapDownloader(5); //where 5 is the number of concurrent downloads permitted
	bm.setErrorDrawable(new ColorDrawable(Color.RED));
	bm.setInProgressDrawable(new ColorDrawable(Color.YELLOW));
	bm.download(url,imageView);
