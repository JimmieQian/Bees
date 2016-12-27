/*
 * Copyright (C) 2011 The Android Open Source Project
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

package qian.jimmie.cn.volley.volley.request;


import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.widget.ImageView;

import qian.jimmie.cn.volley.volley.Bees;
import qian.jimmie.cn.volley.volley.cache.HttpHeaderParser;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.exception.NoSuchMethodError;
import qian.jimmie.cn.volley.volley.exception.ParseError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;
import qian.jimmie.cn.volley.volley.respone.Response;
import qian.jimmie.cn.volley.volley.utils.ImageUtils;


/**
 * A canned request for getting an image at a given URL and calling
 * back with a decoded Bitmap.
 */
public class ImageRequest extends Request<Bitmap> {
    /**
     * Socket timeout in milliseconds for image requests
     */
    private static final int IMAGE_TIMEOUT_MS = 1000;

    /**
     * Default number of retries for image requests
     */
    private static final int IMAGE_MAX_RETRIES = 2;

    /**
     * Default backoff multiplier for image requests
     */
    private static final float IMAGE_BACKOFF_MULT = 2f;

    private Bitmap.Config mDecodeConfig;
    private int mMaxWidth;
    private int mMaxHeight;
    private ImageView.ScaleType mScaleType;
    private ImageView mImageView;

    private int mPreResourceId;
    private int mErrResourceId;

    /**
     * Decoding lock so that we don't decode more than one image at a time (to avoid OOM's)
     */
    private static final Object sDecodeLock = new Object();

    public ImageRequest() {
        super(IMAGE_BACKOFF_MULT);
        setMethod(Bees.Method.GET);
        setRetryTimes(IMAGE_MAX_RETRIES);
        setTimeOut(IMAGE_TIMEOUT_MS);
    }

    public Request setResolution(int maxWidth, int maxHeight) {
        this.mMaxWidth = maxWidth;
        this.mMaxHeight = maxHeight;
        return this;
    }

    public Request setScaleType(ImageView.ScaleType scaleType) {
        this.mScaleType = scaleType;
        return this;
    }

    public Request setDecodeConfig(Bitmap.Config config) {
        this.mDecodeConfig = config;
        return this;
    }

    public Request into(ImageView view) {
        this.mImageView = view;
        if (mPreResourceId != 0 && mImageView != null) {
            Bitmap bm = ImageUtils.zipBitmap(mImageView.getResources(), mPreResourceId, mMaxWidth, mMaxHeight, mScaleType, mDecodeConfig);
            mImageView.setImageBitmap(bm);
        }
        return this;
    }

    public Request setPreIconId(@DrawableRes int id) {
        this.mPreResourceId = id;
        return this;
    }

    public Request setErrIconId(@DrawableRes int id) {
        this.mErrResourceId = id;
        return this;
    }

    @Override
    public Request setErrListener(Response.ErrorListener errListener) {
        throw new NoSuchMethodError("imageRequest not support this method,please use `setErrIconId()` instead");
    }

    @Override
    public Request setListener(Response.Listener listener) {
        throw new NoSuchMethodError("imageRequest not support this method,please use `into()` instead");
    }

    @Override
    protected void onFinish() {
        VolleyLog.e("销毁了吗?.....");
        mImageView = null;
    }

    @Override
    public Priority getPriority() {
        return Priority.LOW;
    }


    @Override
    public Response<Bitmap> parseNetworkResponse(NetworkResponse response) {
        // Serialize all decode on a global lock to reduce concurrent heap usage.
        synchronized (sDecodeLock) {
            try {
                return doParse(response);
            } catch (OutOfMemoryError e) {
                VolleyLog.e("Caught OOM for %d byte image, url=%s", response.data.length, getUrl());
                return Response.error(new ParseError(e));
            }
        }
    }

    /**
     * The real guts of parseNetworkResponse. Broken out for readability.
     */
    private Response<Bitmap> doParse(NetworkResponse response) {
        byte[] data = response.data;
        Bitmap bitmap;
        bitmap = ImageUtils.zipBitmap(data, mMaxWidth, mMaxHeight, mScaleType, mDecodeConfig);

        if (bitmap == null) {
            return Response.error(new ParseError(response));
        } else {
            return Response.success(bitmap, HttpHeaderParser.parseCacheHeaders(response));
        }
    }

    @Override
    public void deliverResponse(Bitmap response) {
        if (mImageView == null) throw new NullPointerException("imageView == null ");
        mImageView.setImageBitmap(response);
    }

    @Override
    public void deliverError(GreeError error) {
        if (mImageView != null && mErrResourceId != 0) {
            Bitmap bm = ImageUtils.zipBitmap(mImageView.getResources(), mErrResourceId, mMaxWidth, mMaxHeight, mScaleType, mDecodeConfig);
            mImageView.setImageBitmap(bm);
        }
    }
}
