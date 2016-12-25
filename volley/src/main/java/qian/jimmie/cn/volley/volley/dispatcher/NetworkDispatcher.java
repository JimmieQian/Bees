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

package qian.jimmie.cn.volley.volley.dispatcher;


import android.annotation.TargetApi;
import android.net.TrafficStats;
import android.os.Build;
import android.os.SystemClock;

import java.util.concurrent.BlockingQueue;

import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.core.interfaces.Network;
import qian.jimmie.cn.volley.volley.core.interfaces.ResponseDelivery;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * Provides a thread for performing network dispatch from a queue of requests.
 * <p>
 * Requests added to the specified queue are processed from the network via a
 * specified {@link Network} interface. Responses are committed to cache, if
 * eligible, using a specified {@link Cache} interface. Valid responses and
 * errors are posted back to the caller via a {@link ResponseDelivery}.
 */
public class NetworkDispatcher extends Thread {
    /**
     * The queue of requests to service.
     */
    private final BlockingQueue<Request<?>> mQueue;
    /**
     * The network interface for processing requests.
     */
    private final Network mNetwork;
    /**
     * The cache to write to.
     */
    private final Cache mCache;
    /**
     * For posting responses and errors.
     */
    private final ResponseDelivery mDelivery;
    /**
     * Used for telling us to die.
     */
    private volatile boolean mQuit = false;

    public NetworkDispatcher(BlockingQueue<Request<?>> queue,
                             Network network, Cache cache,
                             ResponseDelivery delivery) {
        mQueue = queue;
        mNetwork = network;
        mCache = cache;
        mDelivery = delivery;
    }

    public void quit() {
        mQuit = true;
        interrupt();
    }

    /**
     * 添加流量的监控,对调试有帮助(对实际请求无用)
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addTrafficStatsTag(Request<?> request) {
        // Tag the request (if API >= 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag());
        }
    }

    @Override
    public void run() {
        // 设置线程的优先级
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        Request<?> request;

        while (true) {
            long startTimeMs = SystemClock.elapsedRealtime();
            // 去掉request占用,避免内存泄露
            request = null;
            try {
                request = mQueue.take();
            } catch (InterruptedException e) {
                if (mQuit) {
                    return;
                }
                continue;
            }

            try {
                request.addMarker("network-queue-take");

                // If the request was cancelled already, do not perform the
                // network request.
                if (request.isCanceled()) {
                    request.finish("network-discard-cancelled");
                    continue;
                }

                // 流量监控
                addTrafficStatsTag(request);

                /**
                 * 分发网络请求 ==> netWork (负责处理相应) ==> stack (真正处理请求的类)
                 */
                NetworkResponse networkResponse = mNetwork.performRequest(request);

                request.addMarker("network-http-complete");

                // 如果是304,并且已经处理了,就直接结束请求
                if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                    request.finish("not-modified");
                    continue;
                }

                // 将networkRespone转化为response
                Response<?> response = request.parseNetworkResponse(networkResponse);
                request.addMarker("network-parse-complete");

                // 如果需要缓存
                if (request.shouldCache() && response.cacheEntry != null) {
                    mCache.put(request.getCacheKey(), response.cacheEntry);
                    request.addMarker("network-cache-written");
                }

                // Post the response back.
                request.markDelivered();
                mDelivery.postResponse(request, response);
            } catch (GreeError volleyError) {
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                parseAndDeliverNetworkError(request, volleyError);
            } catch (Exception e) {
                VolleyLog.e(e, "Unhandled exception %s", e.toString());
                GreeError volleyError = new GreeError(e);
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                mDelivery.postError(request, volleyError);
            }
        }
    }

    private void parseAndDeliverNetworkError(Request<?> request, GreeError error) {
        error = request.parseNetworkError(error);
        mDelivery.postError(request, error);
    }
}
