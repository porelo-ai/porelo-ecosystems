package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.fuin.porelo.ecosystem.api.EcosystemProtocol;

/**
 * The one place in the adapter that opens a connection. One instance serves one call: its limit
 * runs from when it is made.
 */
final class VendorHttp implements ToolDownloads.Fetch, AutoCloseable {
    private static final Duration PER_REQUEST =
            Duration.ofSeconds(EcosystemProtocol.RESOLVE_REQUEST_SECONDS);
    private static final Duration PER_CALL =
            Duration.ofSeconds(EcosystemProtocol.RESOLVE_CALL_SECONDS);
    static final int MOST_BYTES = 16 * 1024 * 1024;

    private final HttpClient client;
    private final Duration perRequest;
    private final long deadline;

    /**
     * Connects as the person's own settings say.
     *
     * @param proxies The proxy variables passed in, by name; never read from the environment.
     * @throws ToolUnavailable when a proxy variable cannot be read
     */
    VendorHttp(Map<String, String> proxies) {
        this(proxies, PER_REQUEST, PER_CALL);
    }

    VendorHttp(Map<String, String> proxies, Duration perRequest, Duration perCall) {
        this.perRequest = perRequest;
        this.deadline = System.nanoTime() + perCall.toNanos();
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(perRequest)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .proxy(selector(proxies))
                        .build();
    }

    // The whole exchange counts, redirects and body too: the client's own timeout ends at headers.
    @Override
    public String text(URI uri) throws IOException {
        long left = Math.min(perRequest.toNanos(), deadline - System.nanoTime());
        if (left <= 0) {
            throw new IOException("the call's time is up");
        }
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(perRequest)
                        .header("User-Agent", "porelo-ecosystem-maven")
                        .GET()
                        .build();
        CompletableFuture<HttpResponse<byte[]>> exchange =
                client.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.limiting(
                                HttpResponse.BodyHandlers.ofByteArray(), MOST_BYTES));
        try {
            HttpResponse<byte[]> response = exchange.get(left, TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("status " + response.statusCode());
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new IOException("no answer in time", e);
        } catch (ExecutionException e) {
            throw new IOException("no answer", e);
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Ends what the call left open; the adapter's process may serve another. */
    @Override
    public void close() {
        client.shutdownNow();
    }

    /** Chooses per host, as the person's {@code NO_PROXY} says. */
    static ProxySelector selector(Map<String, String> proxies) {
        Optional<InetSocketAddress> address = proxy(proxies);
        List<String> direct =
                Arrays.stream(
                                proxies.getOrDefault(
                                                "NO_PROXY", proxies.getOrDefault("no_proxy", ""))
                                        .split(","))
                        .map(name -> name.strip().toLowerCase(Locale.ROOT))
                        .filter(name -> !name.isEmpty())
                        .toList();
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return address.isEmpty() || uri.getHost() != null && namedIn(direct, uri.getHost())
                        ? List.of(Proxy.NO_PROXY)
                        : List.of(new Proxy(Proxy.Type.HTTP, address.get()));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress where, IOException why) {
                // A request says its own failure.
            }
        };
    }

    private static boolean namedIn(List<String> direct, String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return direct.stream()
                .map(entry -> entry.startsWith(".") ? entry.substring(1) : entry)
                .anyMatch(
                        entry ->
                                entry.equals("*")
                                        || name.equals(entry)
                                        || name.endsWith("." + entry));
    }

    /**
     * The proxy's address alone: what it says of a user stays out of every answer.
     *
     * @throws ToolUnavailable when it cannot be read, without saying what it holds
     */
    static Optional<InetSocketAddress> proxy(Map<String, String> proxies) {
        for (String name : List.of("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy")) {
            String written = proxies.get(name);
            if (written != null && !written.isBlank()) {
                URI uri = readable(name, written);
                int port = uri.getPort() == -1 ? 80 : uri.getPort();
                // A written proxy is taken or refused, never passed by for a direct connection.
                if (uri.getHost() == null || port < 1 || port > 65_535) {
                    throw new ToolUnavailable(name + " could not be read");
                }
                return Optional.of(
                        InetSocketAddress.createUnresolved(
                                uri.getHost().toLowerCase(Locale.ROOT), port));
            }
        }
        return Optional.empty();
    }

    // The cause's message would quote a password, so it goes no further.
    private static URI readable(String name, String written) {
        try {
            return new URI(written.contains("://") ? written : "http://" + written);
        } catch (java.net.URISyntaxException e) {
            throw new ToolUnavailable(name + " could not be read");
        }
    }
}
