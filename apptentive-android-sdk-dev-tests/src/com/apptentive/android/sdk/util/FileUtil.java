/*
 * Copyright (c) 2014, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.util;

import android.content.Context;
import android.content.res.AssetManager;
import com.apptentive.android.sdk.Log;

import java.io.*;

/**
 * @author Sky Kelsey
 */
public class FileUtil {
	private final static int READ_BUF_LEN = 2048;

	public static String loadFileAssetAsString(Context context, String path) {
		Reader reader = null;
		try {
			StringBuilder builder = new StringBuilder();
			reader = openBufferedReaderFromFileAsset(context, path);

			char[] buf = new char[READ_BUF_LEN];
			int count = 0;
			while ((count = reader.read(buf, 0, READ_BUF_LEN)) != -1) {
				builder.append(buf, 0, count);
			}
			return builder.toString();
		} catch (IOException e) {
			Log.e("Error reading asset as string.", e);
		} finally {
			Util.ensureClosed(reader);
		}
		return null;
	}

	public static BufferedReader openBufferedReaderFromFileAsset(Context context, String path) throws IOException {
		AssetManager assetManager = context.getResources().getAssets();
		return new BufferedReader(new InputStreamReader(assetManager.open(path)));
	}
}
