package com.flurryos.launcher;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-side endpoint for the FlurryOS host bridge. */
public final class BridgeService extends Service implements BridgeSocketServer.Handler {
    public static final String SOCKET_NAME = "flurryos-bridge";
    private static final String ACTION_MAIN = Intent.ACTION_MAIN;
    private static final String CATEGORY_LAUNCHER = Intent.CATEGORY_LAUNCHER;
    private static final String PACKAGE_PATTERN = "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*";
    private static final int MAX_ID_LENGTH = 64;
    private BridgeSocketServer socketServer;
    private ExecutorService work;

    @Override public void onCreate() {
        super.onCreate();
        work = Executors.newSingleThreadExecutor();
        socketServer = new BridgeSocketServer(SOCKET_NAME, this);
        try {
            socketServer.start();
        } catch (Exception exception) {
            stopSelf();
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public JSONObject handle(JSONObject request) {
        final String id = request.optString("id", "");
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
        if (!request.has("method")) throw new IllegalArgumentException("falta method");
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
        String packageName = args.optString("package", "");
        String activityName = args.optString("activity", "");
        if (!packageName.matches(PACKAGE_PATTERN)) throw new IllegalArgumentException("package inválido");
        Intent intent;
        if (activityName.length() > 0) {
            if (!activityName.matches("[A-Za-z0-9_.$]+")) throw new IllegalArgumentException("activity inválida");
            intent = new Intent(ACTION_MAIN);
            intent.addCategory(CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(packageName, activityName));
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
        if (work != null) work.shutdownNow();
        super.onDestroy();
    }
}
