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


import android.os.Handler;

import java.util.concurrent.Executor;

import qian.jimmie.cn.volley.volley.core.interfaces.ResponseDelivery;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * 分发 响应和错误
 * Delivers responses and errors.
 */
public class ExecutorDelivery implements ResponseDelivery {
    /**
     * 利用handler,用于将 runnable 回调回主线程
     */
    private final Executor mResponsePoster;

    /**
     * 传入一个handler 一般是持有 mainLooper 的handler
     */
    public ExecutorDelivery(final Handler handler) {
        // Make an Executor that just wraps the handler.
        mResponsePoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * 传入执行者 executor
     */
    public ExecutorDelivery(Executor executor) {
        mResponsePoster = executor;
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response) {
        postResponse(request, response, null);
    }

    /**
     * @param request  请求(用于判断请求是否被取消)
     * @param response 响应()
     * @param runnable .
     */
    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        // 响应正常,并且已被分发
        request.markDelivered();
        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }

    @Override
    public void postError(Request<?> request, GreeError error) {
        request.addMarker("post-error");
        Response<?> response = Response.error(error);
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, null));
    }

    /**
     * 将请求的返回结果回调给主线程处理
     * A Runnable used for delivering network responses to a listener on the
     * main thread.
     */
    @SuppressWarnings("rawtypes")
    private class ResponseDeliveryRunnable implements Runnable {
        private final Request mRequest;
        private final Response mResponse;
        private final Runnable mRunnable;

        public ResponseDeliveryRunnable(Request request, Response response, Runnable runnable) {
            mRequest = request;
            mResponse = response;
            mRunnable = runnable;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            // 请求取消,直接结束请求,不做分发处理
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }

            // 正常处理结果,成功,失败
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }
            // 与缓存有关的设置 (如果还需处理,则不结束请求, 会执行runnable)
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                mRequest.finish("done");
            }

            // 如果添加runnable非空,则运行runnable
            if (mRunnable != null) {
                mRunnable.run();
            }
        }
    }
}
