package com.flurryos.launcher;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Android-side endpoint for the FlurryOS host bridge. */
public final class BridgeService extends Service implements BridgeSocketServer.Handler {
    public static final String SOCKET_NAME = "flurryos-bridge";
    private static final String ACTION_MAIN = Intent.ACTION_MAIN;
    private static final String CATEGORY_LAUNCHER = Intent.CATEGORY_LAUNCHER;
    private static final String PACKAGE_PATTERN = "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*";
    private static final int MAX_ID_LENGTH = 64;
    private BridgeSocketServer socketServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        socketServer = new BridgeSocketServer(SOCKET_NAME, this);
        try {
            socketServer.start();
        } catch (Exception exception) {
            stopSelf();
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public JSONObject handle(JSONObject request) {
        Object rawId = request.opt("id");
        final String id = rawId instanceof String ? (String) rawId : "";
        try {
            validateRequest(request, id);
            String method = request.getString("method");
            if ("ping".equals(method)) return success(id, new JSONObject().put("message", "pong"));
            if ("capabilities".equals(method)) return capabilities(id);
            if ("apps.list".equals(method)) return listApps(id);
            if ("app.launch".equals(method)) return launch(id, request.optJSONObject("args"));
            return BridgeSocketServer.error(id, "METHOD_NOT_FOUND", "método no permitido");
        } catch (Exception exception) {
            return BridgeSocketServer.error(id, "REQUEST_INVALID", exception.getMessage() == null ? "solicitud inválida" : exception.getMessage());
        }
    }

    private static void validateRequest(JSONObject request, String id) throws Exception {
        if (request.optInt("version", -1) != 1) throw new IllegalArgumentException("versión no soportada");
        if (id.length() == 0 || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("id inválido");
        Object rawMethod = request.opt("method");
        if (!(rawMethod instanceof String) || ((String) rawMethod).length() == 0 || ((String) rawMethod).length() > 64) {
            throw new IllegalArgumentException("method inválido");
        }
        if (!request.has("args")) request.put("args", new JSONObject());
        if (request.optJSONObject("args") == null) {
            throw new IllegalArgumentException("args debe ser un objeto");
        }
    }

    private JSONObject capabilities(String id) throws Exception {
        JSONObject result = new JSONObject();
        result.put("catalog", "packagemanager");
        result.put("launch", "explicit-intent");
        result.put("install", "package-installer");
        result.put("socket", SOCKET_NAME);
        return success(id, result);
    }

    private JSONObject listApps(String id) throws Exception {
        Intent query = new Intent(ACTION_MAIN);
        query.addCategory(CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        Collections.sort(infos, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo left, ResolveInfo right) {
                return label(left).compareToIgnoreCase(label(right));
            }
        });
        JSONArray apps = new JSONArray();
        for (ResolveInfo info : infos) {
            ActivityInfo activity = info.activityInfo;
            JSONObject app = new JSONObject();
            app.put("label", label(info));
            app.put("package", activity.packageName);
            app.put("activity", activity.name);
            apps.put(app);
        }
        return success(id, new JSONObject().put("apps", apps));
    }

    private JSONObject launch(String id, JSONObject args) throws Exception {
        if (args == null) throw new IllegalArgumentException("faltan args");
        final JSONObject launchArgs = args;
        FutureTask<JSONObject> task = new FutureTask<>(() -> launchOnMainThread(id, launchArgs));
        mainHandler.post(task);
        try {
            return task.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new IllegalStateException("tiempo agotado al lanzar la aplicación");
        }
    }

    private JSONObject launchOnMainThread(String id, JSONObject args) throws Exception {
        Object rawPackage = args.opt("package");
        Object rawActivity = args.opt("activity");
        String packageName = rawPackage instanceof String ? (String) rawPackage : "";
        String activityName = rawActivity instanceof String ? (String) rawActivity : "";
        if (!packageName.matches(PACKAGE_PATTERN)) throw new IllegalArgumentException("package inválido");
        Intent intent;
        if (activityName.length() > 0) {
            if (!activityName.matches("[A-Za-z0-9_.$]+")) throw new IllegalArgumentException("activity inválida");
            intent = new Intent(ACTION_MAIN);
            intent.addCategory(CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(packageName, activityName));
            ResolveInfo resolved = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null || !packageName.equals(resolved.activityInfo.packageName)) {
                throw new IllegalArgumentException("actividad no resoluble");
            }
        } else {
            intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) throw new IllegalArgumentException("actividad lanzable no encontrada");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return success(id, new JSONObject().put("package", packageName).put("activity", activityName));
    }

    private String label(ResolveInfo info) {
        CharSequence value = info.loadLabel(getPackageManager());
        return value == null ? info.activityInfo.packageName : value.toString();
    }

    private static JSONObject success(String id, JSONObject result) throws Exception {
        return new JSONObject().put("version", 1).put("id", id).put("ok", true).put("result", result);
    }

    @Override public void onDestroy() {
        if (socketServer != null) socketServer.close();
        super.onDestroy();
    }
}
