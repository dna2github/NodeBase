package net.seven.nodebase.nodebase;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class NodeAppMonitor extends Thread {
    private enum STAT {
        UNKNOWN,
        BORN,
        READY,
        RUNNING,
        DEAD
    }

    private STAT nodebaseStat;
    private Process nodebaseProcess;
    private final String nodebaseName;
    private final String[] nodebaseCmd;
    private final HashMap<String, String> nodebaseEnv;

    public NodeAppMonitor(String name, String[] cmd, HashMap<String, String> env) {
        super();
        this.nodebaseName = name;
        this.nodebaseCmd = cmd;
        this.nodebaseEnv = new HashMap<>();
        if (env != null) this.nodebaseEnv.putAll(env);
        this.nodebaseStat = STAT.BORN;
        this.nodebaseProcess = null;
    }

    public boolean nodebaseIsRunning() { return STAT.RUNNING == this.nodebaseStat; }
    public boolean nodebaseIsReady() { return STAT.READY == this.nodebaseStat; }
    public boolean nodebaseIsDead() { return STAT.DEAD == this.nodebaseStat; }

    private int nodebasePid() {
        Process p = this.nodebaseProcess;
        if (p == null) return -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!p.isAlive()) return -1;
        }
        Class<? extends Process> k = p.getClass();
        if ("java.lang.UNIXProcess".equals(k.getName())) {
            try {
                int pid = -1;
                Field f = k.getDeclaredField("pid");
                f.setAccessible(true);
                // this try to make sure if getInt throw an error,
                // `setAccessible(false)` can be executed
                // so that `pid` is protected after this access
                try { pid = f.getInt(p); } catch (IllegalAccessException e) { }
                f.setAccessible(false);
                return pid;
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return -1;
    }

    private ArrayList<Integer> nodebaseChildrenPids(int pid) {
        // XXX maybe we can get sub processes pids from a stable way like /proc
        //     for a workaround, currently we just use `ps`
        ArrayList<Integer> children = new ArrayList<>();
        String output = Command.checkOutput(new String[] {
                "/system/bin/ps",
                "-o", "pid=", "--ppid", String.valueOf(pid)
        });
        if (output == null || output.length() == 0) return children;
        String[] lines = output.trim().split("\n");
        for(String line : lines) {
            line = line.trim();
            if (line.length() == 0) continue;
            // TODO: try...catch...
            int cpid = Integer.parseInt(line);
            if (pid != cpid) children.add(cpid);
        }
        return children;
    }

    private int[] nodebaseCollectPidsForKilling(ArrayList<Integer> collected, int pid, boolean nested) {
        if (pid < 0) return new int[0];
        if (collected == null) collected = new ArrayList<>();
        ArrayList<Integer> pids = nodebaseChildrenPids(pid);
        for (int childPid : pids) {
            if (childPid < 0) continue;
            collected.add(childPid);
            Logger.i(
                    "NodeBase",
                    "monitor",
                    String.format(
                            Locale.getDefault(),
                            "killing (%d / %s) -> %d | parent=%d",
                            getId(), this.nodebaseName,
                            childPid, pid)
            );
            if (nested) nodebaseCollectPidsForKilling(collected, childPid, true);
        }
        int n = collected.size();
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = collected.get(i);
        }
        return r;
    }

    public void nodebaseStart() {
        this.start();
    }

    public boolean nodebaseStop() {
        // XXX only RUNNING app can be stopped to avoid race condition
        //     stop non-RUNNING app will return false
        if (this.nodebaseProcess == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!this.nodebaseProcess.isAlive()) return false;
        }
        int[] pids = this.nodebaseCollectPidsForKilling(null, this.nodebasePid(), true);
        for (int pid : pids) {
            android.os.Process.killProcess(pid);
        }
        this.nodebaseProcess.destroy();
        return true;
    }

    public NodeAppMonitor nodebaseRestart() {
        this.nodebaseStop();
        NodeAppMonitor m = new NodeAppMonitor(this.nodebaseName, this.nodebaseCmd, this.nodebaseEnv);
        m.start();
        return m;
    }

    @Override
    public void run() {
        Handler uiRunner;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            uiRunner = Handler.createAsync(Looper.getMainLooper());
        } else {
            uiRunner = new Handler(Looper.getMainLooper());
        }
        try {
            this.nodebaseStat = STAT.READY;
            // TODO event/onReady
            Logger.i(
                    "NodeBase",
                    "monitor",
                    String.format(
                            Locale.getDefault(),
                            "starting (%d / %s) - %s",
                            getId(), this.nodebaseName,
                            String.join(" ", this.nodebaseCmd))
            );

            // set RUNNING before set up the process
            // when stop the service, it guarantees that no race condition for
            // setting nodebaseStat
            this.nodebaseStat = STAT.RUNNING;
            ProcessBuilder build = new ProcessBuilder(this.nodebaseCmd);
            // XXX: do we need to check some important variable should not be changed
            //      e.g. HOME, USER, PWD, ...
            build.environment().putAll(this.nodebaseEnv);
            this.nodebaseProcess = build.start();
            // TODO event/onStart
            Logger.i(
                    "NodeBase",
                    "monitor",
                    String.format(
                            Locale.getDefault(),
                            "started (%d / %s)",
                            getId(), this.nodebaseName)
            );

            uiRunner.post(new Runnable() {
                @Override
                public void run() {
                    ArrayList<String> r = new ArrayList<>();
                    r.add("start");
                    r.add(nodebaseName);
                    NodeAppService.getEventHandler().postMessage("app", r);
                }
            });

            this.nodebaseProcess.waitFor();
        } catch (IOException e) {
            Logger.e(
                    "NodeService",
                    "monitor",
                    String.format(
                            Locale.getDefault(),
                            "io error (%d / %s) - %s",
                            getId(), this.nodebaseName, e)
            );
            this.nodebaseProcess = null;
            // TODO event/onError
        } catch (InterruptedException e) {
            Logger.e(
                    "NodeService",
                    "monitor",
                    String.format(
                            Locale.getDefault(),
                            "interrupted error (%d / %s) - %s",
                            getId(), this.nodebaseName, e)
            );
            // TODO event/onError
        }

        this.nodebaseStat = STAT.DEAD;
        // TODO: event/onPost
        Logger.e(
                "NodeService",
                "monitor",
                String.format(
                        Locale.getDefault(),
                        "stopped (%d / %s)",
                        getId(), this.nodebaseName)
        );

        uiRunner.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> r = new ArrayList<>();
                r.add("stop");
                r.add(nodebaseName);
                NodeAppService.getEventHandler().postMessage("app", r);
            }
        });
    }
}
