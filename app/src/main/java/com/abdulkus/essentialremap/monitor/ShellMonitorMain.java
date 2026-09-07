package com.abdulkus.essentialremap.monitor;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** app_process entry point, running as shell. No Context, network, alarms, or idle wake lock. */
public final class ShellMonitorMain {
    private static final String PACKAGE = "com.abdulkus.essentialremap";
    private static final Pattern INPUT = Pattern.compile(
        "\\[\\s*(\\d+)\\.(\\d{6})\\]\\s+(?:/dev/input/[^:]+:\\s+)?([0-9a-fA-F]{4})\\s+([0-9a-fA-F]{4})\\s+([0-9a-fA-F]{8})");
    private final String session = UUID.randomUUID().toString().replace("-", "");
    private final int appUid;
    private final File directory;
    private final ArrayBlockingQueue<MonitorMessage> outbound = new ArrayBlockingQueue<>(32);
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private final MonitorGestureClassifier classifier;
    private volatile java.lang.Process inputProcess;
    private volatile Connection connection;
    private volatile boolean inputReady;
    private long messageNumber;
    private volatile long lastPhysicalElapsed;

    private ShellMonitorMain(int appUid, File directory) {
        this.appUid = appUid;
        this.directory = directory;
        timers.setRemoveOnCancelPolicy(true);
        classifier = new MonitorGestureClassifier(new MonitorGestureClassifier.Scheduler() {
            public long nowMs() { return SystemClock.uptimeMillis(); }
            public MonitorGestureClassifier.Cancel schedule(long delayMs, Runnable task) {
                java.util.concurrent.ScheduledFuture<?> future = timers.schedule(task, delayMs, TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            }
        }, this::emit, 500, 300);
    }

    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000 || args.length != 2) {
            throw new IllegalArgumentException("Expected shell UID, application UID and monitor directory");
        }
        int uid = Integer.parseInt(args[0]);
        if (uid < 10000) throw new IllegalArgumentException("Invalid application UID");
        new ShellMonitorMain(uid, new File(args[1])).run();
    }

    private void run() throws Exception {
        directory.mkdirs();
        // Binding the abstract socket is also an atomic, kernel-owned single-instance lock.
        LocalServerSocket server = new LocalServerSocket(MonitorMessage.SOCKET);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                java.lang.Process process = inputProcess;
                if (process != null) process.destroy();
                new File(directory, "key-monitor.state").delete();
            }));
            writeFile("key-monitor.pid", Integer.toString(android.os.Process.myPid()));
            thread("essential-input", this::readInput);
            thread("essential-delivery", this::deliver);
            while (true) { // Blocks in accept; does not wake the device to check anything.
                LocalSocket socket = server.accept();
                if (socket.getPeerCredentials().getUid() != appUid) {
                    socket.close();
                    continue;
                }
                Connection next = new Connection(socket);
                Connection previous = connection;
                connection = next;
                if (previous != null) previous.close();
                thread("essential-ack", next::readReplies);
                emit(inputReady ? "READY" : "RESET", 0, 0);
            }
        } finally { server.close(); }
    }

    private void readInput() {
        int failures = 0;
        while (failures < 5) {
            long began = SystemClock.elapsedRealtime();
            try {
                String device = findInput();
                java.lang.Process process = new ProcessBuilder("/system/bin/getevent", "-t", device)
                    .redirectErrorStream(true).start();
                inputProcess = process;
                inputReady = true;
                writeFile("key-monitor.state", "revision=" + MonitorMessage.REVISION + " pid=" +
                    android.os.Process.myPid() + " input=" + device + " session=" + session);
                emit("READY", 0, 0);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Can't enable monotonic clock")) {
                            throw new IllegalStateException("Input monotonic clock unavailable");
                        }
                        Matcher event = INPUT.matcher(line);
                        if (!event.find()) continue;
                        int type = Integer.parseInt(event.group(3), 16);
                        int code = Integer.parseInt(event.group(4), 16);
                        if (type == 0 && code == 3) { // SYN_DROPPED: do not execute a partial gesture.
                            classifier.reset();
                            emit("RESET", 0, 0);
                        }
                        if (type != 1 || code != 250) continue;
                        long ns = Long.parseLong(event.group(1)) * 1_000_000_000L + Long.parseLong(event.group(2)) * 1_000L;
                        long age = SystemClock.uptimeMillis() - ns / 1_000_000L;
                        if (age < -100 || age > MonitorMessage.MAX_AGE_MS) {
                            classifier.reset();
                            emit("RESET", 0, 0);
                            continue;
                        }
                        lastPhysicalElapsed = SystemClock.elapsedRealtime();
                        failures = 0;
                        long value = Long.parseLong(event.group(5), 16);
                        if (value == 1) classifier.down(ns);
                        else if (value == 0) classifier.up(ns);
                        // EV_KEY repeat is intentionally ignored.
                    }
                }
                log("input EOF");
            } catch (Exception error) {
                log("input failure " + error.getClass().getSimpleName() + ": " + error.getMessage());
            } finally {
                inputReady = false;
                classifier.reset();
                java.lang.Process process = inputProcess;
                if (process != null) process.destroyForcibly();
                inputProcess = null;
                new File(directory, "key-monitor.state").delete();
                emit("RESET", 0, 0);
            }
            // Retry only after an actual reader failure. Five fast failures stop the helper.
            if (SystemClock.elapsedRealtime() - began > 60_000) failures = 0;
            failures++;
            if (failures < 5) SystemClock.sleep(250L << (failures - 1));
        }
        log("input recovery exhausted; explicit restart required");
        System.exit(1);
    }

    private String findInput() throws Exception {
        File[] devices = new File("/sys/class/input").listFiles(file -> file.getName().matches("event[0-9]+"));
        if (devices != null) {
            Arrays.sort(devices);
            for (File device : devices) {
                try (BufferedReader name = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(new File(device, "device/name")), StandardCharsets.UTF_8))) {
                    if ("gpio-keys".equals(name.readLine())) return "/dev/input/" + device.getName();
                } catch (Exception ignored) { }
            }
        }
        // Some ROMs restrict sysfs to shell. This bounded fallback runs only at startup/recovery.
        File[] inputs = new File("/dev/input").listFiles(file -> file.getName().matches("event[0-9]+"));
        if (inputs != null) {
            Arrays.sort(inputs);
            for (File input : inputs) {
                String properties = command(1_000, "/system/bin/getevent", "-pl", input.getPath());
                if (properties.contains("gpio-keys")) return input.getPath();
            }
        }
        throw new IllegalStateException("gpio-keys not found");
    }

    private synchronized void emit(String kind, long down, long event) {
        MonitorMessage message = new MonitorMessage(session, ++messageNumber, kind, down, event, SystemClock.elapsedRealtime());
        if (!outbound.offer(message)) {
            outbound.clear();
            outbound.offer(new MonitorMessage(session, ++messageNumber, "RESET", 0, 0, SystemClock.elapsedRealtime()));
            log("delivery overflow; partial sequence discarded");
        }
    }

    private void deliver() {
        while (true) {
            try {
                MonitorMessage message = outbound.take(); // No idle timer or polling.
                if (!message.fresh(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())) {
                    log("stale delivery discarded kind=" + message.kind);
                    continue;
                }
                Connection active = connection;
                if (active != null && active.send(message)) continue;
                // A protected explicit broadcast can cold-start the app after process death.
                // Never spawn a command on the input-reading thread, and always bound its lifetime.
                String response = command(1_200, "/system/bin/cmd", "activity", "broadcast", "--user", "0",
                    "-f", "0x10000000", "-a", PACKAGE + ".SHELL_KEY_EVENT", "-n", PACKAGE + "/.ShellKeyEventReceiver",
                    "--es", "bridge_message", message.encode());
                log("fallback kind=" + message.kind + " number=" + message.number + " result=" + response.replace('\n', ' '));
            } catch (Exception error) {
                log("delivery failure " + error.getClass().getSimpleName());
            }
        }
    }

    private final class Connection {
        private final LocalSocket socket;
        private final PrintWriter writer;
        private long acknowledged;
        private boolean closed;
        Connection(LocalSocket socket) throws Exception {
            this.socket = socket;
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        synchronized boolean send(MonitorMessage message) {
            if (closed) return false;
            writer.println(message.encode());
            if (writer.checkError()) { close(); return false; }
            long deadline = SystemClock.elapsedRealtime() + 750;
            while (!closed && acknowledged < message.number) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) { close(); return false; }
                try { wait(remaining); } catch (InterruptedException error) { close(); return false; }
            }
            return acknowledged >= message.number;
        }
        void readReplies() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && line.length() < 80) {
                    if (line.startsWith("ACK ")) {
                        long number = Long.parseLong(line.substring(4));
                        synchronized (this) { acknowledged = Math.max(acknowledged, number); notifyAll(); }
                    } else if (line.equals("PING")) {
                        java.lang.Process process = inputProcess;
                        emit(inputReady && process != null && process.isAlive() ? "READY" : "RESET", 0, 0);
                    }
                }
            } catch (Exception ignored) { } finally { close(); }
        }
        synchronized void close() {
            if (closed) return;
            closed = true;
            try { socket.close(); } catch (Exception ignored) { }
            if (connection == this) connection = null;
            notifyAll();
        }
    }

    private static String command(long timeoutMs, String... args) throws Exception {
        java.lang.Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) throw new IllegalStateException("command timeout");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && output.length() < 8192) output.append(line).append('\n');
                if (process.exitValue() != 0) throw new IllegalStateException("command exit=" + process.exitValue());
                return output.toString();
            }
        } finally { process.destroyForcibly(); }
    }

    private void writeFile(String name, String text) throws Exception {
        File temporary = new File(directory, name + ".new");
        try (FileOutputStream output = new FileOutputStream(temporary)) { output.write((text + "\n").getBytes(StandardCharsets.UTF_8)); }
        if (!temporary.renameTo(new File(directory, name))) throw new IllegalStateException("state rename");
    }
    private synchronized void log(String text) {
        try {
            File file = new File(directory, "key-monitor.log");
            if (file.length() > 64 * 1024) {
                File old = new File(directory, "key-monitor.log.1");
                old.delete();
                file.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((SystemClock.elapsedRealtime() + " " + text + " lastInput=" + lastPhysicalElapsed + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }
    private static void thread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
