package com.jiguang.jpush;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.flutter.view.FlutterNativeView;

/** JPushPlugin */
public class JPushPlugin implements MethodCallHandler, PluginRegistry.NewIntentListener {

    /** Plugin registration. */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "jpush");
        channel.setMethodCallHandler(new JPushPlugin(registrar, channel));

        registrar.addViewDestroyListener(new PluginRegistry.ViewDestroyListener() {
            @Override
            public boolean onViewDestroy(FlutterNativeView flutterNativeView) {
                instance.dartIsReady = false;
                return false;
            }
        });

        registrar.addNewIntentListener(instance);
    }

    private Map<String, Object> convertJsonStringToMap(String extras) {
        if (extras == null) return null;
        try {
            return convertJsonObjectToMap(new JSONObject(extras));
        } catch (JSONException e) {
            return null;
        }
    }

    private Map<String, Object> convertJsonObjectToMap(JSONObject json) {
        final Map<String, Object> map = new HashMap<>();
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);
            if (value instanceof JSONObject) {
                map.put(key, convertJsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, convertJsonArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<Object> convertJsonArrayToList(JSONArray json) {
        int length = json.length();
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object value = json.opt(i);
            if (value instanceof JSONObject) {
                list.add(convertJsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(convertJsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    private static String TAG = "| JPUSH | Flutter | Android | ";
    public static JPushPlugin instance;
    static final List<Map<String, Object>> openNotificationCache = new ArrayList<>();

    private boolean dartIsReady = false;
    private boolean jpushDidinit = false;

    private List<Result> getRidCache;

    private final Registrar registrar;
    private final MethodChannel channel;
    public final Map<Integer, Result> callbackMap;
    private int sequence;

    private Map<String, Object> launchAppNotification;

    private JPushPlugin(Registrar registrar, MethodChannel channel) {

        this.registrar = registrar;
        this.channel = channel;
        this.callbackMap = new HashMap<>();
        this.sequence = 0;
        this.getRidCache = new ArrayList<>();

        instance = this;

        handleIntent(registrar.context(), registrar.activity().getIntent(), true);
    }


    @Override
    public boolean onNewIntent(Intent intent) {
        handleIntent(registrar.context(), intent, false);
        return false;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        Log.i(TAG,call.method);
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (call.method.equals("getLaunchAppNotification")) {
            getLaunchAppNotification(call, result);
        }
        else {
            result.notImplemented();
        }
    }

    // 主线程再返回数据
    public void runMainThread(final Map<String,Object> map, final Result result, final String method) {
        Log.d(TAG,"runMainThread:" + "map = " + map + ",method =" + method);
        android.os.Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (result == null && method != null){
                    channel.invokeMethod(method,map);
                } else {
                    result.success(map);
                }
            }
        });
    }

    public void getLaunchAppNotification(MethodCall call, Result result) {
        Log.d(TAG,"");

        result.success(launchAppNotification);
    }

    private void handleIntent(Context context, Intent intent, boolean initial) {
        if (intent == null) return;

        //获取华为平台附带的jpush信息
        String data = intent.getDataString();
        //获取fcm、oppo平台附带的jpush信息
        if (TextUtils.isEmpty(data) && intent.getExtras() != null) {
            data = intent.getExtras().getString("JMessageExtra");
        }
        if (TextUtils.isEmpty(data)) return;

        try {
            JSONObject jsonObject = new JSONObject(data);
            String msgId = jsonObject.optString("msg_id");
            byte whichPushSDK = (byte) jsonObject.optInt("rom_type");
            String title = jsonObject.optString("n_title");
            String content = jsonObject.optString("n_content");
            String extras = jsonObject.optString("n_extras");

            Map<String, Object> extrasMap = convertJsonStringToMap(extras);
            if (initial) {
                if (extrasMap != null) {
                    launchAppNotification = new HashMap<>();
                    launchAppNotification.put("extras", extrasMap);
                }
            } else {
                transmitNotificationOpen(title, content, extrasMap);
            }
        } catch (JSONException e) {
            Log.w(TAG, "parse notification error");
        }
    }

    /// 检查当前应用的通知开关是否开启
    static void transmitNotificationOpen(String title, String alert, Map<String, Object> extras) {
        Log.d(TAG,"transmitNotificationOpen " + "title=" + title + "alert=" + alert + "extras=" + extras);

        Map<String, Object> notification= new HashMap<>();
        notification.put("title", title);
        notification.put("alert", alert);
        notification.put("extras", extras);
        synchronized (JPushPlugin.openNotificationCache) {
            JPushPlugin.openNotificationCache.add(notification);
        }

        if (instance == null) {
            Log.d("JPushPlugin", "the instance is null");
            return;
        }

        if (instance.dartIsReady) {
            Log.d("JPushPlugin", "instance.dartIsReady is true");
            JPushPlugin.instance.channel.invokeMethod("onOpenNotification", notification);
            synchronized (JPushPlugin.openNotificationCache) {
                JPushPlugin.openNotificationCache.remove(notification);
            }
        }

    }

}
