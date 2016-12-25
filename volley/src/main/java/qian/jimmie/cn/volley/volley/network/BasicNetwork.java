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

package qian.jimmie.cn.volley.volley.network;

import android.os.SystemClock;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import qian.jimmie.cn.volley.volley.constance.HttpStatus;
import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.core.interfaces.HttpStack;
import qian.jimmie.cn.volley.volley.core.interfaces.Network;
import qian.jimmie.cn.volley.volley.core.interfaces.RetryPolicy;
import qian.jimmie.cn.volley.volley.exception.AuthFailureError;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.exception.NetworkError;
import qian.jimmie.cn.volley.volley.exception.NoConnectionError;
import qian.jimmie.cn.volley.volley.exception.ServerError;
import qian.jimmie.cn.volley.volley.exception.TimeoutError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;
import qian.jimmie.cn.volley.volley.utils.DateUtils;

/**
 * A network performing Volley requests over an {@link HttpStack}.
 */
public class BasicNetwork implements Network {
    protected static final boolean DEBUG = VolleyLog.DEBUG;

    private static int SLOW_REQUEST_THRESHOLD_MS = 3000;


    /**
     * 请求堆栈 使用httpURLconnection处理请求
     */
    protected final HttpStack mHttpStack;


    public BasicNetwork(HttpStack httpStack) {
        mHttpStack = httpStack;
    }

    /**
     * 通过stack处理请求,得到HttpResponse,在处理,如304缓存处理等,返回NetworkResponse
     *
     * @param request Request to process
     * @return NetworkResponse
     * @throws GreeError
     */
    @Override
    public NetworkResponse performRequest(Request<?> request) throws GreeError {
        long requestStart = SystemClock.elapsedRealtime();
        NetworkResponse httpResponse = null;
        while (true) {
            httpResponse = null;
            Map<String, String> responseHeaders = Collections.emptyMap();
            try {
                Map<String, String> headers = new HashMap<String, String>();
                // 获取的添加cache头部
                addCacheHeaders(headers, request.getCacheEntry());

                // 将请求和cache头部交给stack处理,返回响应
                httpResponse = mHttpStack.performRequest(request, headers);

                if (httpResponse == null) throw new IOException("get none response");

                int statusCode = httpResponse.statusCode;

                // 处理缓存验证(304 内容未修改)
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    httpResponse.notModified = true;
                    httpResponse.networkTimeMs = SystemClock.elapsedRealtime() - requestStart;
                    // 如果是304 通过cache分发,使用request.setCahceEntry.
                    Cache.Entry entry = request.getCacheEntry();
                    if (entry == null) {
                        return httpResponse;
                    }

                    // 将头部信息传入cache中
                    entry.responseHeaders.putAll(responseHeaders);
                    httpResponse.data = entry.data;
                    httpResponse.headers = entry.responseHeaders;
                    // 从cache中得到的数据信息
                    return httpResponse;
                }

//                // 处理重定向 301 | 302 / 无需处理重定向 交给 httpURLconnection处理
//                if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
//                    // 从 Location 字段 获取新的URL
//                    String newUrl = responseHeaders.get("Location");
//                    // 设置重定向地址
//                    request.setRedirectUrl(newUrl);
//                }

                httpResponse.data = httpResponse.data == null ? new byte[1] : httpResponse.data;

                // 打log
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                logSlowRequests(requestLifetime, request, httpResponse.data, statusCode);

                // 如果是非 2XX, 302,301,304 的状态码,则抛出io异常
                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }

                return httpResponse;
                /**
                 * 异常处理
                 */
            } catch (SocketTimeoutException e) {
                // 通信超时 . 尝试重试
                attemptRetryOnException("socket", request, new TimeoutError());
            } catch (MalformedURLException e) {
                throw new RuntimeException("Bad URL " + request.getUrl(), e);
            } catch (IOException e) {
                if (httpResponse == null) {
                    attemptRetryOnException("connection failed", request, new NoConnectionError());
                    continue;
                }
                if (httpResponse.hasError) {
                    attemptRetryOnException("get err data ...", request, new NoConnectionError());
                    continue;
                }
                int statusCode = httpResponse.statusCode;
                VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());
                // 如果实体不是空
                httpResponse.networkTimeMs = SystemClock.elapsedRealtime() - requestStart;
                // 401 402 权限问题,尝试重试
                if (statusCode >= 400 || statusCode <= 499) {
                    attemptRetryOnException("auth",
                            request, new AuthFailureError(httpResponse));
                    continue;
                } else if (statusCode >= 500) {
                    // 5xx 服务端问题
                    throw new ServerError(httpResponse);
                }
                attemptRetryOnException("got unCatchException ...", request, new NetworkError());
            }
        }
    }

    private void logSlowRequests(long requestLifetime, Request<?> request,
                                 byte[] responseContents, int code) {
        if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
            VolleyLog.d("HTTP response for request=<%s> [lifetime=%d], [size=%s], " +
                            "[rc=%d], [retryCount=%s]", request, requestLifetime,
                    responseContents != null ? responseContents.length : "null",
                    code, request.getRetryPolicy().getCurrentRetryCount());
        }
    }

    /**
     * 尝试重试机制
     *
     * @param request The request to use.
     */
    private static void attemptRetryOnException(String logPrefix, Request<?> request,
                                                GreeError exception) throws GreeError {
        // 获取重传机制
        RetryPolicy retryPolicy = request.getRetryPolicy();
        int oldTimeout = request.getTimeoutMs();

        try {
            retryPolicy.retry(exception);
        } catch (GreeError e) {
            request.addMarker(String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
    }

    /**
     * 添加缓存头部
     *
     * @param headers .
     * @param entry   .
     */
    private void addCacheHeaders(Map<String, String> headers, Cache.Entry entry) {
        // 找不到缓存实体,则返回
        if (entry == null) {
            return;
        }

        if (entry.etag != null) {
            headers.put("If-None-Match", entry.etag);
        }

        // 记录的最后一次修改时间
        if (entry.lastModified > 0) {
            Date refTime = new Date(entry.lastModified);
            headers.put("If-Modified-Since", DateUtils.formatDate(refTime));
        }
    }

    protected void logError(String what, String url, long start) {
        long now = SystemClock.elapsedRealtime();
        VolleyLog.v("HTTP ERROR(%s) %d ms to fetch %s", what, (now - start), url);
    }

}
