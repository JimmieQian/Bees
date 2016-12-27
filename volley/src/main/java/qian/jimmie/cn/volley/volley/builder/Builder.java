package qian.jimmie.cn.volley.volley.builder;

import java.lang.ref.WeakReference;
import java.util.Map;

import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * Created by jimmie on 16/12/27.
 */

public abstract class Builder<T> {
    Request request;
    RequestQueue queue;

    public Builder(RequestQueue queue, Request request) {
        this.queue = new WeakReference<>(queue).get();
        this.request = request;
    }

    Builder setMethod(int method) {
        request.setMethod(method);
        return this;
    }

    Builder setUrl(String url) {
        request.setUrl(url);
        return this;
    }

    Builder setHeaders(Map<String, String> headers) {
        request.setHeaders(headers);
        return this;
    }

    Builder addHeader(String key, String value) {
        request.addHeader(key, value);
        return this;
    }

    Builder setParams(Map<String, String> params) {
        request.setParams(params);
        return this;
    }

    Builder addParam(String key, String value) {
        request.addParam(key, value);
        return this;
    }

    Builder setRetryTimes(int times) {
        request.setRetryTimes(times);
        return this;
    }

    abstract Builder setListener(Response.Listener<T> listener);

    Builder shouldCache(boolean shouldCache) {
        request.setShouldCache(shouldCache);
        return this;
    }

    Builder setErrListener(Response.ErrorListener listener) {
        request.setErrListener(listener);
        return this;
    }

    void build() {
        queue.add(request);
        // 回收builder的成员变量
        queue = null;
        request = null;
    }

}
