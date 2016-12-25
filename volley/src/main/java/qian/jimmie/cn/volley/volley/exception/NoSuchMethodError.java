package qian.jimmie.cn.volley.volley.exception;

/**
 * Created by jimmie on 16/12/25.
 */

public class NoSuchMethodError extends Error {
    public NoSuchMethodError() {
        this("no such method!");
    }

    public NoSuchMethodError(String message) {
        super(message);
    }
}
