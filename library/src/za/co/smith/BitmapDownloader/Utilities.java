package za.co.smith.BitmapDownloader;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Utilities {

	static String md5(String s) {
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

}
