package com.abdulkus.essentialremap;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import com.abdulkus.essentialremap.monitor.MonitorMessage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Test APK only: exercises the real shell-to-app socket and ACK path on Android. */
public final class SocketProbeMain {
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new AssertionError("probe is not shell");
        LocalServerSocket server = new LocalServerSocket(MonitorMessage.SOCKET);
        try {
            LocalSocket peer = server.accept();
            try {
                if (peer.getPeerCredentials().getUid() != Integer.parseInt(args[0])) throw new AssertionError("unexpected app UID");
                peer.setSoTimeout(3000);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                String session = UUID.randomUUID().toString().replace("-", "");
                send(out, in, new MonitorMessage(session, 1, "READY", 0, 0, SystemClock.elapsedRealtime()));
                long down = SystemClock.uptimeMillis() * 1_000_000L;
                send(out, in, new MonitorMessage(session, 2, "DOWN", down, down, SystemClock.elapsedRealtime()));
                SystemClock.sleep(60);
                MonitorMessage tap = new MonitorMessage(session, 3, "SINGLE", down,
                    SystemClock.uptimeMillis() * 1_000_000L, SystemClock.elapsedRealtime());
                send(out, in, tap);
                send(out, in, tap); // Lost ACK/retry must not duplicate execution.
                peer.setSoTimeout(600);
                try { if (in.readLine() != null) throw new AssertionError("unexpected idle traffic"); }
                catch (SocketTimeoutException expected) { }
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
