package qian.jimmie.cn.my_volley;


import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import qian.jimmie.cn.volley.volley.Bees;
import qian.jimmie.cn.volley.volley.core.RequestQueue;
import qian.jimmie.cn.volley.volley.exception.GreeError;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.respone.Response;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Volley";
    RequestQueue requestQueue;
    ImageView view;

    String url = "https://www.baidu.com/s?wd=android";
    final String[] urls = {
            "http://pic.7kk.com/simg/1/800_0/0/6a/9abbde3d3f7beab7a8b587ae75c57.jpg",
            "http://pic.7kk.com/simg/1/800_0/d/48/166b822b12c2d172dd17dbc296d16.jpg",
            "httpa://pic.7kk.com/simg/1/800_0/2/8b/426c2d9631ad97a41eb082f6e416a.jpg",
            "httpa://pic.7kk.com/simg/1/800_0/f/4e/60cb63efbbf34896fe3855cc7a80e.jpg",
            "httpa://pic.7kk.com/simg/1/800_0/9/03/b507ae640f5711b728adb0a553fc6.jpg",
            "http://pic.7kk.com/simg/1/800_0/2/52/7353e6d3af5e9b0667f15031aeb1f.jpg",
            "http://pic.7kk.com/simg/1/800_0/8/b5/eb906a5c0f54508776d0d0b572494.jpg"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        view = (ImageView) findViewById(R.id.imageView);

        initVolley();
        initBees();


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                Log.e(TAG, "onClick: ");
                volleyClick();
            }
        });
    }

    private void initBees() {
        Bees.newRequestQueue(this, 100 * 1024 * 1024);
        VolleyLog.setDebugable(true);
    }


    private void beesClick() {
        int num = (int) (Math.random() * 7);
        String url = urls[num];
        Bees.newImageRequest()
                .setUrl(url)
                .shouldCache(false)
                .setDecodeConfig(Bitmap.Config.ARGB_4444)
                .setScaleType(ImageView.ScaleType.CENTER_CROP)
                .setResolution(200, 400)
                .setPreIconId(R.drawable.loading)
                .setErrIconId(R.drawable.err)
                .into(view);

        Bees.newStringRequest()
                .setUrl(this.url)
                .setMethod(Bees.Method.GET)
                .setListener(new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.e(TAG, "onResponse: " + response);
                    }
                })
                .setErrListener(new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(GreeError error) {

                    }
                })
                .build();
    }

    private void initVolley() {
//        requestQueue = Volley.newRequestQueue(this);
    }

    void volleyClick() {
//        ImageRequest request = new ImageRequest(url, new Response.Listener<Bitmap>() {
//            @Override
//            public void onResponse(Bitmap bitmap) {
//                view.setImageBitmap(bitmap);
//            }
//        }, 0, 0, ImageView.ScaleType.CENTER, Bitmap.Config.ARGB_8888, null);
//        requestQueue.add(request);
    }


    public void onBtnClick(View v) {
        beesClick();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }
}
