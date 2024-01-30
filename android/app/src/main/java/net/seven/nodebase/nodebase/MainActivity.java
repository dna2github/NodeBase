package net.seven.nodebase.nodebase;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        listenNodeAppChannel(flutterEngine);
    }

    private void listenNodeAppChannel(@NonNull FlutterEngine flutterEngine) {
        new MethodChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                "net.seven.nodebase/app"
        ).setMethodCallHandler(new MethodChannel.MethodCallHandler() {
            @Override
            public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
                switch(call.method) {
                    case "app.start" -> {
                        String name = call.argument("name");
                        String cmd = call.argument("cmd");
                        if (name == null || cmd == "" || cmd.length() == 0) {
                            result.error("app.start", "empty name or cmd", null);
                            return;
                        }
                        if (startNodeApp(name, cmd) == ChannelResult.OK) {
                            result.success(true);
                        } else {
                            result.error("app.start", "internal error", null);
                        }
                    }
                    case "app.stop" -> {
                        String name = call.argument("name");
                        if (name == null) {
                            result.error("app.stop", "empty name", null);
                            return;
                        }
                        if (stopNodeApp(name) == ChannelResult.OK) {
                            result.success(true);
                        } else {
                            result.error("app.stop", "internal error", null);
                        }
                    }
                    case "app.stat" -> {
                        String name = call.argument("name");
                        if (name == null) {
                            result.error("app.stat", "empty name", null);
                            return;
                        }
                        var stat = getNodeAppStatus(name);
                        if (stat == null) {
                            result.error("app.stat", "no stat", null);
                            return;
                        }
                        result.success(stat);
                    }
                    case "util.ip" -> {
                        var ipJsonString = getIPs();
                        result.success(ipJsonString);
                    }
                    case "util.browser.open" -> {
                        String url = call.argument("url");
                        if (url == null) {
                            result.error("util.browser", "no url", null);
                            return;
                        }
                        External.openBrowser(MainActivity.this.getContext(), url);
                        result.success(true);
                    }
                    case "util.file.executable" -> {
                        String fname = call.argument("filename");
                        if (fname == null) {
                            result.error("util.file.executable", "no filename", null);
                            return;
                        }
                        fileExecutablize(fname);
                        result.success(true);
                    }
                    case "util.arch" -> {
                        result.success(External.getArch());
                    }
                    case "util.workspace" -> {
                        result.success(workspaceBaseDir());
                    }
                }
            }
        });

        new EventChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                "net.seven.nodebase/event"
        ).setStreamHandler(NodeAppService.getEventHandler());
    }

    public ChannelResult startNodeApp(String name, String cmd) {
        Logger.i(
                "NodeBase",
                "startNodeApp",
                String.format("start node app (%s) -> %s", name, cmd));
        Intent it = new Intent(getContext(), ForegroundNodeService.class);
        ContextCompat.startForegroundService(getContext(), it);
        NodeAppService.startNodeApp(name, cmd.split("\u0001"));
        return ChannelResult.OK;
    }

    public ChannelResult stopNodeApp(String name) {
        Logger.i(
                "NodeBase",
                "stopNodeApp",
                String.format("stop node app (%s)", name));
        Intent it = new Intent(getContext(), ForegroundNodeService.class);
        if (NodeAppService.getRunningNodeAppCount() <= 1) {
            stopService(it);
        } else {
            NodeAppService.stopNodeApp(name);
        }
        return ChannelResult.OK;
    }

    public HashMap<String, Object> getNodeAppStatus(String name) {
        // TODO get NodeMonitor and assemble data as json
        NodeAppMonitor app = NodeAppService.getNodeApp(name);
        HashMap<String, Object> json = new HashMap<>();
        String state = "none";
        if (app != null) {
            if (app.nodebaseIsReady()) state = "new";
            else if (app.nodebaseIsRunning()) state = "running";
            else if (app.nodebaseIsDead()) state = "dead";
            json.put("state", state);
        }
        return json;
    }

    public HashMap<String, ArrayList<String>> getIPs() {
        var json = Network.getIPs();
        return json;
    }

    public boolean fileExecutablize(String fname) {
        File f = new File(fname);
        return f.setExecutable(true, true);
    }

    public String workspaceBaseDir() {
        return getContext().getApplicationInfo().dataDir;
    }
}
