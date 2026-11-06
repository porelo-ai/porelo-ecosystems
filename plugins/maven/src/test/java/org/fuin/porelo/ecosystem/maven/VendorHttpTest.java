package org.fuin.porelo.ecosystem.maven;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Harm prevented: a lookup hangs a first start, ignores the proxy, or says its credential. */
class VendorHttpTest {
    private static final Duration SHORT = Duration.ofMillis(300);

    @ParameterizedTest
    @CsvSource({
        "HTTPS_PROXY, http://proxy.example:3128, proxy.example, 3128",
        "https_proxy, proxy.example:3128, proxy.example, 3128",
        "HTTP_PROXY, http://proxy.example, proxy.example, 80",
        "HTTPS_PROXY, http://user:secret@proxy.example:3128, proxy.example, 3128"
    })
    void theProxyIsItsAddressAlone(String name, String written, String host, int port) {
        var address = VendorHttp.proxy(Map.of(name, written)).orElseThrow();

        assertThat(address.getHostString()).as("no credential in it").isEqualTo(host);
        assertThat(address.getPort()).as("its port").isEqualTo(port);
    }

    @Test
    void aProxyThatCannotBeReadSaysNothingOfIt() {
        assertThatThrownBy(() -> VendorHttp.proxy(Map.of("HTTPS_PROXY", "http://u:s#cret@p:1 x")))
                .as("a password can stand in it")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageNotContaining("cret")
                .hasNoCause();
    }

    /** Harm prevented: the person's proxy is passed by, or the lookup ends in a crash. */
    @ParameterizedTest
    @CsvSource({
        "http://u:p@ss@proxy.example.org:3128",
        "http://corp_proxy.example.org:3128",
        "http://proxy.example.org:abc",
        "http://:3128",
        "http://proxy.example.org:99999"
    })
    void aProxyWithNoHostOrPortToTakeIsRefusedNotPassedBy(String written) {
        assertThatThrownBy(() -> VendorHttp.proxy(Map.of("HTTPS_PROXY", written)))
                .as("never a direct connection in its place")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessage("HTTPS_PROXY could not be read");
    }

    @ParameterizedTest
    @CsvSource({
        "*, api.github.com, true",
        "'api.github.com,example.org', api.github.com, true",
        ".github.com, api.github.com, true",
        "github.com, api.github.com, true",
        "hub.com, api.github.com, false",
        "example.org, api.github.com, false"
    })
    void noProxyGoesDirectToWhatItNames(String noProxy, String host, boolean direct) {
        var chosen =
                VendorHttp.selector(
                                Map.of(
                                        "HTTPS_PROXY",
                                        "http://proxy.example:3128",
                                        "NO_PROXY",
                                        noProxy))
                        .select(URI.create("https://" + host + "/"));

        assertThat(chosen.get(0).equals(Proxy.NO_PROXY)).as(host).isEqualTo(direct);
    }

    @Test
    @Timeout(10)
    void aBodyThatStopsCostsOneRequestsTime() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try (Socket client = server.accept()) {
                                    client.getOutputStream()
                                            .write(
                                                    "HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\nx"
                                                            .getBytes(US_ASCII));
                                    client.getOutputStream().flush();
                                    Thread.sleep(Duration.ofSeconds(30));
                                } catch (IOException | InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            VendorHttp http = new VendorHttp(Map.of(), SHORT, Duration.ofSeconds(5));
            URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/");

            assertThatThrownBy(() -> http.text(uri))
                    .as("headers alone do not end the wait")
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void aRedirectIsFollowed() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/from",
                exchange -> {
                    exchange.getResponseHeaders().add("Location", "/to");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.createContext(
                "/to",
                exchange -> {
                    byte[] body = "here".getBytes(US_ASCII);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/from");

            assertThat(new VendorHttp(Map.of()).text(uri))
                    .as("any server may answer with one")
                    .isEqualTo("here");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aCallsTimeIsSharedByItsRequests() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write(new byte[] {'x'});
                    exchange.close();
                });
        server.start();
        try {
            VendorHttp http = new VendorHttp(Map.of(), SHORT, Duration.ZERO);
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");

            assertThatThrownBy(() -> http.text(uri))
                    .as("no request starts past the call's limit")
                    .isInstanceOf(IOException.class);
        } finally {
            server.stop(0);
        }
    }
}
