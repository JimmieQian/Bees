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


import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import qian.jimmie.cn.volley.volley.Bees;
import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.core.interfaces.RetryPolicy;
import qian.jimmie.cn.volley.volley.exception.AuthFailureError;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.network.DefaultRetryPolicy;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;
import qian.jimmie.cn.volley.volley.respone.Response;
import qian.jimmie.cn.volley.volley.utils.InternalUtils;

/**
 * 所有网络请求的基类
 * 实现 Comparable 接口,目的是为了加入 缓存和网络的优先级队列中.(PriorityBlockingQueue)
 *
 * @param <T> The type of parsed response this request expects.
 */
public abstract class Request<T> implements Comparable<Request<T>> {

    /**
     * 默认的post和put的编码格式
     * See {@link #getParamsEncoding()}.
     */
    private static final String DEFAULT_PARAMS_ENCODING = "UTF-8";


    /**
     * 日志类
     * An event log tracing the lifetime of this request; for debugging.
     */
    private final VolleyLog.MarkerLog mEventLog = VolleyLog.MarkerLog.ENABLED ? new VolleyLog.MarkerLog() : null;

    /**
     * 请求方法
     */
    private int mMethod;

    /**
     * 请求的URL
     */
    private String mUrl;

    /**
     * 保存重定向的请求URL response的"location"字段 3XX状态吗
     */
    private String mRedirectUrl;

    /**
     * 请求的唯一标识id
     */
    private String mIdentifier;

    /**
     * (URL中host的哈希码)
     */
    private int mDefaultTrafficStatsTag;

    /**
     * 请求的序列,先进先出原则
     */
    private Integer mSequence;

    /**
     * 请求队列(通过finish结束本请求)
     */
    private RequestQueue mRequestQueue;

    /**
     * 请求是否需要被缓存
     */
    private boolean mShouldCache = true;

    /**
     * 请求是否被取消
     */
    private boolean mCanceled = false;

    /**
     * 请求是否已经被分发
     */
    private boolean mResponseDelivered = false;

    /**
     * 请求的重传策略
     */
    private RetryPolicy mRetryPolicy;

    /**
     * 如果改请求在告诉缓存中,则判断缓存是否过期 (收到304 Not Modified)
     */
    private Cache.Entry mCacheEntry = null;

    /**
     * An opaque token tagging this request; used for bulk cancellation.
     * 请求标志,用于取消
     */
    private Object mTag;

    private Map<String, String> mHeaders;

    private Map<String, String> mParams;

    private Response.ErrorListener mErrorListener;

    private final int type;


    public Request(int type) {
        this(type, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
    }

    public Request(int type, float backoff) {
        this.type = type;
        mMethod = Bees.Method.GET;
        // 设置默认的重传策略
        mRetryPolicy = new DefaultRetryPolicy(backoff);
    }


    public Request setMethod(int method) {
        this.mMethod = method;
        return this;
    }

    public Request setUrl(String url) {
        this.mUrl = url;
        this.mIdentifier = createIdentifier(mMethod, url);
        // host哈希码 (用于流量监控)
        mDefaultTrafficStatsTag = findDefaultTrafficStatsTag(url);
        return this;
    }

    public abstract Request setListener(Response.Listener listener);

    public Request setErrListener(Response.ErrorListener errListener) {
        this.mErrorListener = errListener;
        return this;
    }

    public Request setHeaders(Map<String, String> headers) {
        this.mHeaders = headers;
        return this;
    }

    public Request addHeader(String key, String value) {
        if (mHeaders == null)
            mHeaders = new HashMap<>();
        mHeaders.put(key, value);
        return this;
    }

    public Request setParams(Map<String, String> params) {
        this.mParams = params;
        return this;
    }

    public Request addParam(String key, String value) {
        if (mParams == null)
            mParams = new HashMap<>();
        mParams.put(key, value);
        return this;
    }

    public int getMethod() {
        return mMethod;
    }

    public Request setTag(Object tag) {
        mTag = tag;
        return this;
    }

    public Request setTimeOut(int timeOut) {
        mRetryPolicy.setRetryTimeOut(timeOut);
        return this;
    }

    public Request setRetryTimes(int times) {
        mRetryPolicy.setRetryTimes(times);
        return this;
    }

    public Object getTag() {
        return mTag;
    }

    public int getType() {
        return this.type;
    }

    public int getTrafficStatsTag() {
        return mDefaultTrafficStatsTag;
    }

    /**
     * 返回URL中host的哈希码,没有host则返回0
     */
    private static int findDefaultTrafficStatsTag(String url) {
        if (!TextUtils.isEmpty(url)) {
            Uri uri = Uri.parse(url);
            if (uri != null) {
                String host = uri.getHost();
                if (host != null) {
                    return host.hashCode();
                }
            }
        }
        return 0;
    }

    /**
     * 添加log
     */
    public void addMarker(String tag) {
        if (VolleyLog.MarkerLog.ENABLED) {
            mEventLog.add(tag, Thread.currentThread().getId());
        }
    }

    /**
     * 当请求结束时通知请求队列结束该请求
     * 也是释放log事件
     *
     * @param tag 设置标志,用于log
     */
    public void finish(final String tag) {
        // 通知请求队列结束请求
        if (mRequestQueue != null) {
            mRequestQueue.finish(this);
            onFinish();
        }
        // 释放log事件
        if (VolleyLog.MarkerLog.ENABLED) {
            final long threadId = Thread.currentThread().getId();
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // If we finish marking off of the main thread, we need to
                // actually do it on the main thread to ensure correct ordering.
                Handler mainThread = new Handler(Looper.getMainLooper());
                mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        mEventLog.add(tag, threadId);
                        mEventLog.finish(this.toString());
                    }
                });
                return;
            }

            mEventLog.add(tag, threadId);
            mEventLog.finish(this.toString());
        }
    }

    /**
     * 释放监听器
     */
    protected void onFinish() {
        mErrorListener = null;
    }

    /**
     * 连接请求和请求队列 当请求结束时,通知请求队列
     */
    public Request<?> setRequestQueue(RequestQueue requestQueue) {
        mRequestQueue = requestQueue;
        return this;
    }

    /**
     * 请求的序列,先进先出原则.  Used by {@link RequestQueue}.
     */
    public final Request<?> setSequence(int sequence) {
        mSequence = sequence;
        return this;
    }

    /**
     * 返回请求的序列
     */
    public final int getSequence() {
        if (mSequence == null) {
            throw new IllegalStateException("getSequence called before setSequence");
        }
        return mSequence;
    }

    /**
     * 如果发生重定向 则返回重定向地址 否则返回原地址
     */
    public String getUrl() {
        return (mRedirectUrl != null) ? mRedirectUrl : mUrl;
    }

    /**
     * 获取原地址
     */
    public String getOriginUrl() {
        return mUrl;
    }

    /**
     * 返回唯一标示
     */
    public String getIdentifier() {
        return mIdentifier;
    }

    /**
     * 设置重定向地址
     */
    public void setRedirectUrl(String redirectUrl) {
        mRedirectUrl = redirectUrl;
    }

    /**
     * 返回cacheKey , 方法:URL
     */
    public String getCacheKey() {
        return mMethod + ":" + mUrl;
    }

    /**
     * 说明这条请求的内容是从缓存中得到的
     */
    public Request<?> setCacheEntry(Cache.Entry entry) {
        mCacheEntry = entry;
        return this;
    }

    public Cache.Entry getCacheEntry() {
        return mCacheEntry;
    }

    /**
     * 标记请求已经被去掉,不会有回调被分配
     */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * 返回请求是否被取消
     */
    public boolean isCanceled() {
        return mCanceled;
    }

    /**
     * 获取额外的头部信息,需用用户重写,来设置头部信息
     */
    public Map<String, String> getHeaders() throws AuthFailureError {
        return mHeaders == null ? Collections.<String, String>emptyMap() : mHeaders;
    }


    /**
     * 用于设置post参数 , 可被重写修改
     */
    public Map<String, String> getParams() {
        return mParams;
    }

    /**
     * 该编码用于getParams 可被重写修改
     *
     * @return 默认的post方法编码格式
     */
    protected String getParamsEncoding() {
        return DEFAULT_PARAMS_ENCODING;
    }

    /**
     * 返回post参数内容的类型  可被重写修改
     */
    public String getBodyContentType() {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
    }

    /**
     * 根据前面的方法来获取真正的上传实体 用于post
     * getParams()
     * getParamsEncoding()
     */
    public byte[] getBody() throws AuthFailureError {
        Map<String, String> params = getParams();
        if (params != null && params.size() > 0) {
            return encodeParameters(params, getParamsEncoding());
        }
        return null;
    }

    /**
     * 将map形式的实体信息,转化为byte[]
     * Converts <code>params</code> into an application/x-www-form-urlencoded encoded string.
     */
    private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    /**
     * 设置是否需要缓存
     */
    public final Request<?> setShouldCache(boolean shouldCache) {
        mShouldCache = shouldCache;
        return this;
    }

    /**
     * true : 表示需要缓存 (默认开启缓存)
     */
    public final boolean shouldCache() {
        return mShouldCache;
    }

    /**
     * 优先值
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    /**
     * 返回优先级 可被重写修改
     */
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    /**
     * 返回socket timeout(毫秒)
     */
    public final int getTimeoutMs() {
        return mRetryPolicy.getCurrentTimeout();
    }

    /**
     * 返回重传策略
     */
    public RetryPolicy getRetryPolicy() {
        return mRetryPolicy;
    }

    /**
     * 表示该请求已经被分发
     * 可用于request生命周期
     * Mark this request as having a response delivered on it.  This can be used
     * later in the request's lifetime for suppressing identical responses.
     */
    public void markDelivered() {
        mResponseDelivered = true;
    }

    /**
     * true : 表示请求已被分发
     */
    public boolean hasHadResponseDelivered() {
        return mResponseDelivered;
    }

    /**
     * 将NetworkResponse 转化为最终给用户显示的 Response
     */
    abstract public Response<T> parseNetworkResponse(NetworkResponse response);

    /**
     * 错误转化处理
     * Subclasses can override this method to parse 'networkError' and return a more specific error.
     * The default implementation just returns the passed 'networkError'.
     */
    public GreeError parseNetworkError(GreeError volleyError) {
        return volleyError;
    }

    /**
     * Subclasses must implement this to perform delivery of the parsed
     * response to their listeners.  The given response is guaranteed to
     * be non-null; responses that fail to parse are not delivered.
     */
    public abstract void deliverResponse(T response);

    /**
     * Delivers error message to the ErrorListener that the Request was
     * initialized with.
     *
     * @param error Error details
     */
    public void deliverError(GreeError error) {
        if (mErrorListener != null)
            mErrorListener.onErrorResponse(error);
    }

    /**
     * 优先级判断(实现先进先出原则)
     * sequence number to provide FIFO ordering.
     */
    @Override
    public int compareTo(Request<T> other) {
        Priority left = this.getPriority();
        Priority right = other.getPriority();

        // 得到负数,说明优先级更低
        // High-priority requests are "lesser" so they are sorted to the front.
        // Equal priorities are sorted by sequence number to provide FIFO ordering.
        return left == right ?
                this.mSequence - other.mSequence :
                right.ordinal() - left.ordinal();
    }

    @Override
    public String toString() {
        String trafficStatsTag = "0x" + Integer.toHexString(getTrafficStatsTag());
        return (mCanceled ? "[X] " : "[ ] ") + getUrl() + " " + trafficStatsTag + " "
                + getPriority() + " " + mSequence;
    }

    private static long sCounter;

    /**
     * 为请求生成唯一标识
     * sha1(Request:method:url:timestamp:counter)
     *
     * @param method http method
     * @param url    http request url
     * @return sha1 hash string
     */
    private static String createIdentifier(final int method, final String url) {
        return InternalUtils.sha1Hash("Request:" + method + ":" + url +
                ":" + System.currentTimeMillis() + ":" + (sCounter++));
    }
}
