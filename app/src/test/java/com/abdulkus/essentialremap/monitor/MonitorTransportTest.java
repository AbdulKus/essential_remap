package com.abdulkus.essentialremap.monitor;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MonitorTransportTest {
    @Test public void mutualAuthenticationExchangesOnlyAuthenticatedMessages() throws Exception {
        exchange(false);
    }
    @Test public void wrongCredentialCannotReadOrInjectInput() throws Exception {
        exchange(true);
    }
    private void exchange(boolean wrongSecret) throws Exception {
        String secret = MonitorTransport.newSecret();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName(MonitorTransport.HOST))) {
            CompletableFuture<Boolean> accepted = CompletableFuture.supplyAsync(() -> {
                try (Socket peer = listener.accept()) {
                    MonitorTransport channel = MonitorTransport.server(peer, secret);
                    channel.output.println("authenticated");
                    return true;
                } catch (Exception expected) { return false; }
            });
            try (Socket peer = new Socket(MonitorTransport.HOST, listener.getLocalPort())) {
                if (wrongSecret) {
                    try {
                        MonitorTransport.client(peer, MonitorTransport.newSecret());
                        fail("Wrong credential was accepted");
                    } catch (IOException expected) { }
                } else {
                    MonitorTransport channel = MonitorTransport.client(peer, secret);
                    assertEquals("authenticated", MonitorTransport.readLine(channel.input));
                }
            }
            assertEquals(!wrongSecret, accepted.get(3, TimeUnit.SECONDS));
        }
    }
    @Test public void unterminatedAndOversizedMessagesAreRejected() throws Exception {
        for (String text : new String[]{"partial", "a".repeat(241) + "\n"}) {
            try {
                MonitorTransport.readLine(new BufferedReader(new StringReader(text)));
                fail("Malformed frame was accepted");
            } catch (IOException expected) { }
        }
    }
}
