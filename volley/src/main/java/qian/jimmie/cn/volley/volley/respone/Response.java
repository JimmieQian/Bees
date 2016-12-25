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

package qian.jimmie.cn.volley.volley.respone;


import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.exception.GreeError;

/**
 * Encapsulates a parsed response for delivery.
 *
 * @param <T> Parsed type of this response
 */
public class Response<T> {
    public interface ErrorListener<T> {
        void onErrorResponse(GreeError error);
    }

    public interface Listener<T> {
        void onResponse(T response);
    }


    public static <T> Response<T> success(T result, Cache.Entry cacheEntry) {
        return new Response<T>(result, cacheEntry);
    }

    public static <T> Response<T> error(GreeError error) {
        return new Response<T>(error);
    }

    public final T result;

    public final Cache.Entry cacheEntry;

    public final GreeError error;

    /**
     * 表示 softTtl < currentTime (软过期)
     */
    public boolean intermediate = false;

    public boolean isSuccess() {
        return error == null;
    }


    private Response(T result, Cache.Entry cacheEntry) {
        this.result = result;
        this.cacheEntry = cacheEntry;
        this.error = null;
    }

    private Response(GreeError error) {
        this.result = null;
        this.cacheEntry = null;
        this.error = error;
    }
}
