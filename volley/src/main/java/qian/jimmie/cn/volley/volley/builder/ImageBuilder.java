package qian.jimmie.cn.volley.volley.builder;

import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.widget.ImageView;

import java.util.Map;

import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.request.ImageRequest;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * Created by jimmie on 16/12/27.
 */

public class ImageBuilder extends Builder {

    public ImageBuilder(RequestQueue queue, Request request) {
        super(queue, request);
    }

    @Override
    public ImageBuilder setUrl(String url) {
        super.setUrl(url);
        return this;
    }

    @Override
    public ImageBuilder setHeaders(Map headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public ImageBuilder addHeader(String key, String value) {
        super.addHeader(key, value);
        return this;
    }

    @Override
    public ImageBuilder setRetryTimes(int times) {
        super.setRetryTimes(times);
        return this;
    }

    @Override
    public ImageBuilder shouldCache(boolean shouldCache) {
        super.shouldCache(shouldCache);
        return this;
    }

    public ImageBuilder setResolution(int maxWidth, int maxHeight) {
        ((ImageRequest) request).setResolution(maxWidth, maxHeight);
        return this;
    }

    public ImageBuilder setScaleType(ImageView.ScaleType scaleType) {
        ((ImageRequest) request).setScaleType(scaleType);
        return this;
    }

    public ImageBuilder setDecodeConfig(Bitmap.Config config) {
        ((ImageRequest) request).setDecodeConfig(config);
        return this;
    }

    public ImageBuilder into(ImageView view) {
        ((ImageRequest) request).into(view);
        build();
        return this;
    }

    public ImageBuilder setPreIconId(@DrawableRes int id) {
        ((ImageRequest) request).setPreIconId(id);
        return this;
    }

    public ImageBuilder setErrIconId(@DrawableRes int id) {
        ((ImageRequest) request).setErrIconId(id);
        return this;
    }

    @Override
    Builder setListener(Response.Listener listener) {
        return null;
    }
}
