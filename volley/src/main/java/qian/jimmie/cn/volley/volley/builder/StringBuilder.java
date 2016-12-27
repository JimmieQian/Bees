package qian.jimmie.cn.volley.volley.builder;

import java.util.Map;

import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.request.StringRequest;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * Created by jimmie on 16/12/27.
 */

public class StringBuilder extends Builder<String> {

    public StringBuilder(RequestQueue queue, Request request) {
        super(queue, request);
    }

    @Override
    public StringBuilder setUrl(String url) {
        super.setUrl(url);
        return this;
    }

    @Override
    public StringBuilder setHeaders(Map<String, String> headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public StringBuilder addHeader(String key, String value) {
        super.addHeader(key, value);
        return this;
    }

    @Override
    public StringBuilder setRetryTimes(int times) {
        super.setRetryTimes(times);
        return this;
    }

    @Override
    public StringBuilder shouldCache(boolean shouldCache) {
        super.shouldCache(shouldCache);
        return this;
    }

    @Override
    public StringBuilder setMethod(int method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public StringBuilder setListener(Response.Listener<String> listener) {
        ((StringRequest) request).setListener(listener);
        return this;
    }

    @Override
    public StringBuilder setErrListener(Response.ErrorListener listener) {
        super.setErrListener(listener);
        return this;
    }

    @Override
    public StringBuilder addParam(String key, String value) {
        super.addParam(key, value);
        return this;
    }

    @Override
    public StringBuilder setParams(Map<String, String> params) {
        super.setParams(params);
        return this;
    }

    @Override
    public void build() {
        super.build();
    }
}
