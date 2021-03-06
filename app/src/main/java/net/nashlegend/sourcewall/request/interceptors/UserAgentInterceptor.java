package net.nashlegend.sourcewall.request.interceptors;

import static net.nashlegend.sourcewall.BuildConfig.VERSION_CODE;
import static net.nashlegend.sourcewall.BuildConfig.VERSION_NAME;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by NashLegend on 16/6/29.
 */
public class UserAgentInterceptor implements Interceptor {

    volatile private static String userAgent = null;

    synchronized public static void resetUserAgent() {
        userAgent = null;
    }

    /**
     * 返回默认的UserAgent
     */
    private String getDefaultUserAgent() {
        if (userAgent == null) {
            synchronized (UserAgentInterceptor.class) {
                if (userAgent == null) {
                    userAgent = "SourceWall/" + VERSION_NAME + "(" + VERSION_CODE + ")";
                }
            }
        }
        return userAgent;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request().newBuilder()
                .header("User-Agent", getDefaultUserAgent())
                .build();
        return chain.proceed(request);
    }
}