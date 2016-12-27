package qian.jimmie.cn.volley.volley;

import android.content.Context;
import android.os.Environment;

import java.io.File;

import qian.jimmie.cn.volley.volley.builder.ImageBuilder;
import qian.jimmie.cn.volley.volley.builder.StringBuilder;
import qian.jimmie.cn.volley.volley.cache.DiskBasedCache;
import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.core.interfaces.Network;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.network.BasicNetwork;
import qian.jimmie.cn.volley.volley.network.HurlStack;
import qian.jimmie.cn.volley.volley.request.ImageRequest;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.request.StringRequest;

/**
 * function:
 * Created by 2223_yangqianjian on 2016/12/16.
 */

public class Bees {
    private static RequestQueue queue;


    /**
     * Default on-disk cache directory.
     */
    private static final String DEFAULT_CACHE_DIR = "Bees";

    /**
     * 创建一个默认的请求队列,需要在代码中开启队列轮循
     * 设置缓存的最大值(硬盘缓存)
     */
    public static RequestQueue newRequestQueue(Context context, long maxDiskCacheBytes) {
        if (queue != null) return queue;
        File cacheDir;
        if (context == null)
            // 缓存在内部存储上
            cacheDir = new File(Environment.getExternalStorageDirectory(), DEFAULT_CACHE_DIR);
        else cacheDir = context.getCacheDir();

        // 使用 stack 并将结果转换为可被ResponseDelivery处理的NetworkResponse
        Network network = new BasicNetwork(new HurlStack());

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

    public static RequestQueue newRequestQueue() {
        return newRequestQueue(null);
    }

    public static RequestQueue newRequestQueue(Context context) {
        return newRequestQueue(context, 0);
    }

    public static StringBuilder newStringRequest() {
        Request request = new StringRequest();
        return new StringBuilder(queue, request);
    }

    public static ImageBuilder newImageRequest() {
        Request request = new ImageRequest();
        return new ImageBuilder(queue, request);
    }

    /**
     * 支持的请求方法
     */
    public interface Method {
        int GET = 0;
        int POST = 1;
        int HEAD = 4;
    }
}
