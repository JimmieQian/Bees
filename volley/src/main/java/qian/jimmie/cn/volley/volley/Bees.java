package qian.jimmie.cn.volley.volley;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.DrawableRes;
import android.widget.ImageView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;

import qian.jimmie.cn.volley.volley.cache.DiskBasedCache;
import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.core.interfaces.HttpStack;
import qian.jimmie.cn.volley.volley.core.interfaces.Network;
import qian.jimmie.cn.volley.volley.exception.NoSuchMethodError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.network.BasicNetwork;
import qian.jimmie.cn.volley.volley.network.HurlStack;
import qian.jimmie.cn.volley.volley.request.ImageRequest;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.request.StringRequest;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * function:
 * Created by 2223_yangqianjian on 2016/12/16.
 */

public class Bees {
    private static RequestQueue queue;


    /**
     * Default on-disk cache directory.
     */
    private static final String DEFAULT_CACHE_DIR = "volley";

    /**
     * 创建一个默认的请求队列,需要在代码中开启队列轮循
     * 设置缓存的最大值(硬盘缓存)
     */
    public static RequestQueue newRequestQueue(Context context, HttpStack stack, long maxDiskCacheBytes) {
        // 缓存在内部存储上
        File cacheDir = new File(Environment.getExternalStorageDirectory(), DEFAULT_CACHE_DIR);


        if (stack == null) {
            if (Build.VERSION.SDK_INT >= 9) {
                // 初始化请求的堆栈  用于处理请求
                stack = new HurlStack();
            } else {
                throw new NullPointerException("低于9版本不支持");//此行会抛出NullPointedException
            }
        }

        // 使用 stack 并将结果转换为可被ResponseDelivery处理的NetworkResponse
        Network network = new BasicNetwork(stack);

        if (maxDiskCacheBytes <= -1) {
            VolleyLog.e("不进行缓存");
            // 未设置缓存大小 使用默认缓存 (5M)
            queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
        } else {
            // 设置缓存大小 (bytes)
            queue = new RequestQueue(new DiskBasedCache(cacheDir, maxDiskCacheBytes), network);
        }

        // 开始轮循
        queue.start();
        return queue;
    }

    public static RequestQueue newRequestQueue(Context context, int maxDiskCacheBytes) {
        return newRequestQueue(context, null, maxDiskCacheBytes);
    }

    public static RequestQueue newRequestQueue(Context context, HttpStack stack) {
        return newRequestQueue(context, stack, -1);
    }

    public static RequestQueue newRequestQueue(Context context) {
        return newRequestQueue(context, null);
    }

    public static Builder newRequest(int classType) {
        Request request = chooseRequest(classType);
        Builder builder = new Builder(request);
        builder.setRequestQueue(queue);
        return builder;
    }

    private static Request chooseRequest(int type) {
        switch (type) {
            case RequestType.StringRequest:
                return new StringRequest();
            case RequestType.ImageRequest:
                return new ImageRequest();
        }
        return new StringRequest();
    }

    public static class Builder {
        private Request request;
        private RequestQueue queue;

        public Builder(Request request) {
            this.request = request;
        }

        public Builder setMethod(int method) {
            request.setMethod(method);
            return this;
        }

        public Builder setUrl(String url) {
            request.setUrl(url);
            return this;
        }

        public Builder setHeaders(Map<String, String> headers) {
            request.setHeaders(headers);
            return this;
        }

        public Builder addHeader(String key, String value) {
            request.addHeader(key, value);
            return this;
        }

        public Builder setParams(Map<String, String> params) {
            request.setParams(params);
            return this;
        }

        public Builder addParam(String key, String value) {
            request.addParam(key, value);
            return this;
        }

        public Builder setRetryTimes(int times) {
            request.setRetryTimes(times);
            return this;
        }

        public Builder setListener(Response.Listener<?> listener) {
            request.setListener(listener);
            return this;
        }

        public Builder shouldCache(boolean shouldCache) {
            request.setShouldCache(shouldCache);
            return this;
        }

        public Builder setErrListener(Response.ErrorListener listener) {
            request.setErrListener(listener);
            return this;
        }

        public Builder setRequestQueue(RequestQueue queue) {
            this.queue = new WeakReference<>(queue).get();
            return this;
        }

        public void build() {
            queue.add(request);
            // 回收builder的成员变量
            queue = null;
            request = null;
        }

        // for imageRequest
        public Builder setResolution(int maxWidth, int maxHeight) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).setResolution(maxWidth, maxHeight);
            else throw new NoSuchMethodError();
            return this;
        }

        public Builder setScaleType(ImageView.ScaleType scaleType) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).setScaleType(scaleType);
            else throw new NoSuchMethodError();
            return this;
        }

        public Builder setDecodeConfig(Bitmap.Config config) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).setDecodeConfig(config);
            else throw new NoSuchMethodError();
            return this;
        }

        public Builder into(ImageView view) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).into(view);
            else throw new NoSuchMethodError();
            return this;
        }

        public Builder setPreIconId(@DrawableRes int id) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).setPreIconId(id);
            else throw new NoSuchMethodError();
            return this;
        }

        public Builder setErrIconId(@DrawableRes int id) {
            if (request instanceof ImageRequest)
                ((ImageRequest) request).setErrIconId(id);
            else throw new NoSuchMethodError();
            return this;
        }
    }


    public static interface RequestType {
        int StringRequest = 0;
        int ImageRequest = 1;
    }

    /**
     * 支持的请求方法
     */
    public interface Method {
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
        int HEAD = 4;
        int OPTIONS = 5;
        int TRACE = 6;
        int PATCH = 7;
    }
}
