package com.abdulkus.essentialremap.monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Loopback IPC: Android SELinux does not allow app -> shell Unix socket connections. */
public final class MonitorTransport {
    public static final String HOST = "127.0.0.1";
    private static final SecureRandom RANDOM = new SecureRandom();
    public final BufferedReader input;
    public final PrintWriter output;

    private MonitorTransport(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(1500); // Handshake only; no keepalive or idle timeout.
        input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder text = new StringBuilder(64);
        for (byte value : bytes) text.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
        return text.toString();
    }

    public static MonitorTransport client(Socket socket, String secret) throws Exception {
        MonitorTransport channel = new MonitorTransport(socket);
        String clientNonce = newSecret();
        channel.output.println(clientNonce);
        String[] proof = requireLine(channel.input).split(" ", -1);
        if (proof.length != 2 || !validSecret(proof[0]) ||
                !equal(proof[1], mac(secret, "server:" + clientNonce + ":" + proof[0]))) {
            throw new IOException("Monitor authentication failed");
        }
        channel.output.println(mac(secret, "client:" + clientNonce + ":" + proof[0]));
        socket.setSoTimeout(0);
        return channel;
    }

    public static MonitorTransport server(Socket socket, String secret) throws Exception {
        MonitorTransport channel = new MonitorTransport(socket);
        String clientNonce = requireLine(channel.input);
        if (!validSecret(clientNonce)) throw new IOException("Invalid client challenge");
        String serverNonce = newSecret();
        channel.output.println(serverNonce + " " + mac(secret, "server:" + clientNonce + ":" + serverNonce));
        if (!equal(requireLine(channel.input), mac(secret, "client:" + clientNonce + ":" + serverNonce))) {
            throw new IOException("Client authentication failed");
        }
        socket.setSoTimeout(0);
        return channel;
    }

    public static boolean validSecret(String text) { return text != null && text.matches("[a-f0-9]{64}"); }
    private static String mac(String secret, String challenge) throws Exception {
        if (!validSecret(secret)) throw new IOException("Invalid monitor credential");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        byte[] bytes = mac.doFinal(challenge.getBytes(StandardCharsets.US_ASCII));
        StringBuilder text = new StringBuilder(64);
        for (byte value : bytes) text.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
        return text.toString();
    }
    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
    private static String requireLine(BufferedReader input) throws IOException {
        String line = readLine(input);
        if (line == null) throw new IOException("Connection closed");
        return line;
    }
    public static String readLine(BufferedReader input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return line.toString();
            if (line.length() >= 240) throw new IOException("Message too long");
            line.append((char) value);
        }
        if (line.length() != 0) throw new IOException("Truncated message");
        return null;
    }
}
