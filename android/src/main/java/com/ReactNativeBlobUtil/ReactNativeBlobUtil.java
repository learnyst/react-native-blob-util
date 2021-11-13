package com.ReactNativeBlobUtil;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import android.util.SparseArray;
import android.content.ActivityNotFoundException;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

// Cookies
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.network.ForwardingCookieHandler;
import com.facebook.react.modules.network.CookieJarContainer;
import com.facebook.react.modules.network.OkHttpClientProvider;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

import javax.annotation.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.app.Activity.RESULT_OK;
import static com.ReactNativeBlobUtil.ReactNativeBlobUtilConst.GET_CONTENT_INTENT;

public class ReactNativeBlobUtil extends ReactContextBaseJavaModule {

    private final OkHttpClient mClient;

    static ReactApplicationContext RCTContext;
    private static final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(5, 10, 5000, TimeUnit.MILLISECONDS, taskQueue);
    static LinkedBlockingQueue<Runnable> fsTaskQueue = new LinkedBlockingQueue<>();
    private static final ThreadPoolExecutor fsThreadPool = new ThreadPoolExecutor(2, 10, 5000, TimeUnit.MILLISECONDS, taskQueue);
    private static boolean ActionViewVisible = false;
    private static final SparseArray<Promise> promiseTable = new SparseArray<>();

    public ReactNativeBlobUtil(ReactApplicationContext reactContext) {

        super(reactContext);

        mClient = OkHttpClientProvider.getOkHttpClient();
        ForwardingCookieHandler mCookieHandler = new ForwardingCookieHandler(reactContext);
        CookieJarContainer mCookieJarContainer = (CookieJarContainer) mClient.cookieJar();
        mCookieJarContainer.setCookieJar(new JavaNetCookieJar(mCookieHandler));

        RCTContext = reactContext;
        reactContext.addActivityEventListener(new ActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                if (requestCode == GET_CONTENT_INTENT && resultCode == RESULT_OK) {
                    Uri d = data.getData();
                    promiseTable.get(GET_CONTENT_INTENT).resolve(d.toString());
                    promiseTable.remove(GET_CONTENT_INTENT);
                }
            }

            @Override
            public void onNewIntent(Intent intent) {

            }
        });
    }

    @Override
    public String getName() {
        return "ReactNativeBlobUtil";
    }

    @Override
    public Map<String, Object> getConstants() {
        return ReactNativeBlobUtilFS.getSystemfolders(this.getReactApplicationContext());
    }

    @ReactMethod
    public void createFile(final String path, final String content, final String encode, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.createFile(path, content, encode, promise);
            }
        });
    }

    @ReactMethod
    public void createFileASCII(final String path, final ReadableArray dataArray, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.createFileASCII(path, dataArray, promise);
            }
        });
    }

    @ReactMethod
    public void actionViewIntent(String path, String mime, @Nullable String chooserTitle, final Promise promise) {
        try {
            Uri uriForFile = FileProvider.getUriForFile(this.getReactApplicationContext(),
                    this.getReactApplicationContext().getPackageName() + ".provider", new File(path));

            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= 24) {
                // Create the intent with data and type
                intent.setDataAndType(uriForFile, mime);
                if (chooserTitle != null) {
                    intent = Intent.createChooser(intent, chooserTitle);
                }

                // Set flag to give temporary permission to external app to use FileProvider
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // All the activity to be opened outside of an activity
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Validate that the device can open the file
                PackageManager pm = getCurrentActivity().getPackageManager();
                if (intent.resolveActivity(pm) != null) {
                    this.getReactApplicationContext().startActivity(intent);
                }

            } else {
                intent.setDataAndType(Uri.parse("file://" + path), mime).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (chooserTitle != null) {
                    intent = Intent.createChooser(intent, chooserTitle);
                }

                try {
                    this.getReactApplicationContext().startActivity(intent);
                    promise.resolve(true);
                } catch (ActivityNotFoundException ex) {
                    promise.reject("ENOAPP", "No app installed for " + mime);
                }
            }
            ActionViewVisible = true;

            final LifecycleEventListener listener = new LifecycleEventListener() {

                @Override
                public void onHostResume() {
                    if (ActionViewVisible)
                        promise.resolve(null);
                    RCTContext.removeLifecycleEventListener(this);
                }

                @Override
                public void onHostPause() {

                }

                @Override
                public void onHostDestroy() {

                }
            };
            RCTContext.addLifecycleEventListener(listener);
        } catch (Exception ex) {
            promise.reject("EUNSPECIFIED", ex.getLocalizedMessage());
        }
    }

    @ReactMethod
    public void writeArrayChunk(final String streamId, final ReadableArray dataArray, final Callback callback) {
        ReactNativeBlobUtilFS.writeArrayChunk(streamId, dataArray, callback);
    }

    @ReactMethod
    public void unlink(String path, Callback callback) {
        ReactNativeBlobUtilFS.unlink(path, callback);
    }

    @ReactMethod
    public void mkdir(String path, Promise promise) {
        ReactNativeBlobUtilFS.mkdir(path, promise);
    }

    @ReactMethod
    public void exists(String path, Callback callback) {
        ReactNativeBlobUtilFS.exists(path, callback);
    }

    @ReactMethod
    public void cp(final String path, final String dest, final Callback callback) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.cp(path, dest, callback);
            }
        });
    }

    @ReactMethod
    public void mv(String path, String dest, Callback callback) {
        ReactNativeBlobUtilFS.mv(path, dest, callback);
    }

    @ReactMethod
    public void ls(String path, Promise promise) {
        ReactNativeBlobUtilFS.ls(path, promise);
    }

    @ReactMethod
    public void writeStream(String path, String encode, boolean append, Callback callback) {
        new ReactNativeBlobUtilFS(this.getReactApplicationContext()).writeStream(path, encode, append, callback);
    }

    @ReactMethod
    public void writeChunk(String streamId, String data, Callback callback) {
        ReactNativeBlobUtilFS.writeChunk(streamId, data, callback);
    }

    @ReactMethod
    public void closeStream(String streamId, Callback callback) {
        ReactNativeBlobUtilFS.closeStream(streamId, callback);
    }

    @ReactMethod
    public void removeSession(ReadableArray paths, Callback callback) {
        ReactNativeBlobUtilFS.removeSession(paths, callback);
    }

    @ReactMethod
    public void readFile(final String path, final String encoding, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.readFile(path, encoding, promise);
            }
        });
    }

    @ReactMethod
    public void writeFileArray(final String path, final ReadableArray data, final boolean append, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.writeFile(path, data, append, promise);
            }
        });
    }

    @ReactMethod
    public void writeFile(final String path, final String encoding, final String data, final boolean append, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.writeFile(path, encoding, data, append, promise);
            }
        });
    }

    @ReactMethod
    public void lstat(String path, Callback callback) {
        ReactNativeBlobUtilFS.lstat(path, callback);
    }

    @ReactMethod
    public void stat(String path, Callback callback) {
        ReactNativeBlobUtilFS.stat(path, callback);
    }

    @ReactMethod
    public void scanFile(final ReadableArray pairs, final Callback callback) {
        final ReactApplicationContext ctx = this.getReactApplicationContext();
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                int size = pairs.size();
                String[] p = new String[size];
                String[] m = new String[size];
                for (int i = 0; i < size; i++) {
                    ReadableMap pair = pairs.getMap(i);
                    if (pair.hasKey("path")) {
                        p[i] = pair.getString("path");
                        if (pair.hasKey("mime"))
                            m[i] = pair.getString("mime");
                        else
                            m[i] = null;
                    }
                }
                new ReactNativeBlobUtilFS(ctx).scanFile(p, m, callback);
            }
        });
    }

    @ReactMethod
    public void hash(final String path, final String algorithm, final Promise promise) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.hash(path, algorithm, promise);
            }
        });
    }

    /**
     * @param path       Stream file path
     * @param encoding   Stream encoding, should be one of `base64`, `ascii`, and `utf8`
     * @param bufferSize Stream buffer size, default to 4096 or 4095(base64).
     */
    @ReactMethod
    public void readStream(final String path, final String encoding, final int bufferSize, final int tick, final String streamId) {
        final ReactApplicationContext ctx = this.getReactApplicationContext();
        fsThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS fs = new ReactNativeBlobUtilFS(ctx);
                fs.readStream(path, encoding, bufferSize, tick, streamId);
            }
        });
    }

    @ReactMethod
    public void cancelRequest(String taskId, Callback callback) {
        try {
            ReactNativeBlobUtilReq.cancelTask(taskId);
            callback.invoke(null, taskId);
        } catch (Exception ex) {
            callback.invoke(ex.getLocalizedMessage(), null);
        }
    }

    @ReactMethod
    public void slice(String src, String dest, int start, int end, Promise promise) {
        ReactNativeBlobUtilFS.slice(src, dest, start, end, "", promise);
    }

    @ReactMethod
    public void enableProgressReport(String taskId, int interval, int count) {
        ReactNativeBlobUtilProgressConfig config = new ReactNativeBlobUtilProgressConfig(true, interval, count, ReactNativeBlobUtilProgressConfig.ReportType.Download);
        ReactNativeBlobUtilReq.progressReport.put(taskId, config);
    }

    @ReactMethod
    public void df(final Callback callback) {
        fsThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                ReactNativeBlobUtilFS.df(callback, getReactApplicationContext());
            }
        });
    }


    @ReactMethod
    public void enableUploadProgressReport(String taskId, int interval, int count) {
        ReactNativeBlobUtilProgressConfig config = new ReactNativeBlobUtilProgressConfig(true, interval, count, ReactNativeBlobUtilProgressConfig.ReportType.Upload);
        ReactNativeBlobUtilReq.uploadProgressReport.put(taskId, config);
    }

    @ReactMethod
    public void fetchBlob(ReadableMap options, String taskId, String method, String url, ReadableMap headers, String body, final Callback callback) {
        new ReactNativeBlobUtilReq(options, taskId, method, url, headers, body, null, mClient, callback).run();
    }

    @ReactMethod
    public void fetchBlobForm(ReadableMap options, String taskId, String method, String url, ReadableMap headers, ReadableArray body, final Callback callback) {
        new ReactNativeBlobUtilReq(options, taskId, method, url, headers, null, body, mClient, callback).run();
    }

    @ReactMethod
    public void getContentIntent(String mime, Promise promise) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        if (mime != null)
            i.setType(mime);
        else
            i.setType("*/*");
        promiseTable.put(GET_CONTENT_INTENT, promise);
        this.getReactApplicationContext().startActivityForResult(i, GET_CONTENT_INTENT, null);

    }

    @ReactMethod
    public void addCompleteDownload(ReadableMap config, Promise promise) {
        DownloadManager dm = (DownloadManager) RCTContext.getSystemService(RCTContext.DOWNLOAD_SERVICE);
        if (config == null || !config.hasKey("path")) {
            promise.reject("EINVAL", "ReactNativeBlobUtil.addCompleteDownload config or path missing.");
            return;
        }
        String path = ReactNativeBlobUtilFS.normalizePath(config.getString("path"));
        if (path == null) {
            promise.reject("EINVAL", "ReactNativeBlobUtil.addCompleteDownload can not resolve URI:" + config.getString("path"));
            return;
        }
        try {
            WritableMap stat = ReactNativeBlobUtilFS.statFile(path);
            dm.addCompletedDownload(
                    config.hasKey("title") ? config.getString("title") : "",
                    config.hasKey("description") ? config.getString("description") : "",
                    true,
                    config.hasKey("mime") ? config.getString("mime") : null,
                    path,
                    Long.valueOf(stat.getString("size")),
                    config.hasKey("showNotification") && config.getBoolean("showNotification")
            );
            promise.resolve(null);
        } catch (Exception ex) {
            promise.reject("EUNSPECIFIED", ex.getLocalizedMessage());
        }

    }

    @ReactMethod
    public void getSDCardDir(Promise promise) {
        ReactNativeBlobUtilFS.getSDCardDir(this.getReactApplicationContext(), promise);
    }

    @ReactMethod
    public void getSDCardApplicationDir(Promise promise) {
        ReactNativeBlobUtilFS.getSDCardApplicationDir(this.getReactApplicationContext(), promise);
    }

    @ReactMethod
    public void getAllSDCardApplicationDirs(Promise promise) {
        ReactNativeBlobUtil.getAllSDCardApplicationDirs(this.getReactApplicationContext(), promise);
    }    
}
