package com.abdulkus.essentialremap;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import com.abdulkus.essentialremap.monitor.MonitorTransport;
import java.util.concurrent.TimeUnit;
import android.os.SystemClock;
import com.abdulkus.essentialremap.monitor.MonitorMessage;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.util.UUID;

/** Test APK only: exercises the real shell-to-app socket and ACK path on Android. */
public final class SocketProbeMain {
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new AssertionError("probe is not shell");
        ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName(MonitorTransport.HOST));
        String secret = MonitorTransport.newSecret();
        String session = UUID.randomUUID().toString().replace("-", "");
        System.out.println("PROBE_READY");
        System.out.flush();
        try {
            Process bootstrap = new ProcessBuilder("/system/bin/cmd", "activity", "broadcast", "--user", "0",
                "-f", "0x10000000", "-a", "com.abdulkus.essentialremap.SHELL_KEY_EVENT", "-n",
                "com.abdulkus.essentialremap/.ShellKeyEventReceiver", "--es", "bridge_message",
                new MonitorMessage(session, 1, "READY", 0, 0, SystemClock.elapsedRealtime()).encode(),
                "--ei", "bridge_port", Integer.toString(server.getLocalPort()), "--es", "bridge_secret", secret).start();
            if (!bootstrap.waitFor(3, TimeUnit.SECONDS) || bootstrap.exitValue() != 0) {
                bootstrap.destroyForcibly();
                throw new AssertionError("protected bootstrap failed");
            }
            Socket peer = server.accept();
            try {
                MonitorTransport channel = MonitorTransport.server(peer, secret);
                peer.setSoTimeout(3000);
                PrintWriter out = channel.output;
                BufferedReader in = channel.input;
                send(out, in, new MonitorMessage(session, 2, "READY", 0, 0, SystemClock.elapsedRealtime()));
                long down = SystemClock.uptimeMillis() * 1_000_000L;
                send(out, in, new MonitorMessage(session, 3, "DOWN", down, down, SystemClock.elapsedRealtime()));
                long actionNumber = 4;
                if (args.length > 1 && args[1].equals("reconnect")) {
                    peer.close();
                    peer = server.accept();
                    channel = MonitorTransport.server(peer, secret);
                    peer.setSoTimeout(3000);
                    out = channel.output;
                    in = channel.input;
                    send(out, in, new MonitorMessage(session, 4, "READY", 0, 0, SystemClock.elapsedRealtime()));
                    actionNumber = 5;
                } else {
                    SystemClock.sleep(60);
                }
                MonitorMessage tap = new MonitorMessage(session, actionNumber, "SINGLE", down,
                    down + 60_000_000L, SystemClock.elapsedRealtime());
                send(out, in, tap);
                send(out, in, tap); // Lost ACK/retry must not duplicate execution.
                peer.setSoTimeout(600);
                try {
                    String request = in.readLine();
                    if ("PING".equals(request) && args.length > 1 && args[1].equals("reconnect")) {
                        send(out, in, new MonitorMessage(session, actionNumber + 1, "READY", 0, 0, SystemClock.elapsedRealtime()));
                        if (in.readLine() != null) throw new AssertionError("unexpected idle traffic");
                    } else if (request != null) {
                        throw new AssertionError("unexpected idle traffic " + request);
                    }
                } catch (SocketTimeoutException expected) { }
                System.out.println("PROBE_OK");
            } finally { peer.close(); }
        } finally { server.close(); }
    }
    private static void send(PrintWriter out, BufferedReader in, MonitorMessage message) throws Exception {
        out.println(message.encode());
        String response;
        while ((response = in.readLine()) != null) {
            if (response.equals("PING")) continue;
            if (response.equals("ACK " + message.number)) return;
            throw new AssertionError("bad ACK " + response);
        }
        throw new AssertionError("connection closed without ACK");
    }
}
