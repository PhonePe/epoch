package com.phonepe.epoch.server.leadership;

import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.server.managed.LeadershipManager;
import com.phonepe.epoch.server.utils.EpochUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.glassfish.jersey.server.ContainerRequest;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Proxies requests to leader
 */
@Slf4j
@Provider
@Singleton
@Priority(Priorities.USER)
@PreMatching
public class LeaderRoutingFilter implements ContainerRequestFilter {
    private static final Set<String> RESTRICTED_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final Set<String> SKIP_LIST_FILTER = Set.of("housekeeping");

    static {
        RESTRICTED_HEADERS.add("Connection");
        RESTRICTED_HEADERS.add("Host");
        RESTRICTED_HEADERS.add("Content-Length");
        RESTRICTED_HEADERS.add("Accept-Encoding");
        RESTRICTED_HEADERS.add("Transfer-Encoding");
        RESTRICTED_HEADERS.add("Vary");
    }

    private final LeadershipManager manager;
    private final CloseableHttpClient httpClient;

    @Inject
    public LeaderRoutingFilter(LeadershipManager manager) {
        this.manager = manager;
        this.httpClient = buildClient();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (manager.isLeader()) {
            log.trace("Allowing request as this node is the leader. Path:{}", requestContext.getUriInfo().getPath());
            return;
        }
        val leader = manager.leader().orElse(null);
        if (null == leader) {
            requestContext.abortWith(Response.serverError()
                                             .entity(ApiResponse.failure("No leader found in epoch cluster"))
                                             .build());
            return;
        }
        val parts = leader.replaceAll("/", "").split(":");

        /*
        the path from requestContext includes base path - /apis/ui .. which shouldn't be forwarded to the leader, we need to
        sanitize the path such that /ui requests are trimmed while the rest are included with the base path. Hence
        the logic below */
        val decodedPath = ((ContainerRequest) requestContext).getPath(true);
        val destinationUriPath = decodedPath.startsWith("ui/")
               ? decodedPath.replace("ui/", "")
               : ((ContainerRequest) requestContext).getRequestUri().getPath();

        val uri = requestContext.getUriInfo().getRequestUriBuilder()
                .uri(destinationUriPath)
                .scheme(parts[0])
                .host(parts[1])
                .port(Integer.parseInt(parts[2]))
                .build();
        log.trace("Proxying request {} to: {}", requestContext.getUriInfo().getPath(), uri);
        val request = request(uri, (ContainerRequest) requestContext.getRequest());
        try {
            val proxyResponse = httpClient.execute(request, LeaderRoutingFilter::handleResponse);
            val responseBuilder = Response.status(proxyResponse.statusCode()).entity(proxyResponse.body());
            proxyResponse.headers().forEach((name, values) -> {
                if (RESTRICTED_HEADERS.contains(name)) {
                    log.trace("Ignoring header: {}", name);
                }
                else {
                    values.forEach(value -> responseBuilder.header(name, value));
                }
            });
            requestContext.abortWith(responseBuilder.build());
        }
        catch (Exception e) {
            log.error("Error proxying request to " + uri + ": " + EpochUtils.errorMessage(e), e);
            Thread.currentThread().interrupt();
            requestContext.abortWith(
                    Response.serverError()
                            .entity(ApiResponse.failure("Error proxying request due to error: "
                                                                + EpochUtils.errorMessage(e)))
                            .build());
        }
    }

    private static Predicate<String> doesRequestContextPathMatchFilter(final ContainerRequestContext requestContext) {
        return filter -> requestContext.getUriInfo().getRequestUri().getPath().contains(filter);
    }

    private static DroveClient.Response handleResponse(ClassicHttpResponse response) throws IOException,
                                                                                            ParseException {
        val headers = Arrays.stream(response.getHeaders())
                .collect(Collectors.groupingBy(Header::getName,
                                               Collectors.mapping(Header::getValue,
                                                                  Collectors.toUnmodifiableList())));
        return new DroveClient.Response(response.getCode(),
                                        headers,
                                        EntityUtils.toString(response.getEntity()));
    }

    private ClassicHttpRequest request(final URI uri, final ContainerRequest request) {
        val proxyRequest = switch (request.getMethod().toUpperCase()) {
            case "GET" -> new HttpGet(uri);
            case "POST" -> {
                val req = new HttpPost(uri);
                copyBody(request, req);
                yield req;
            }
            case "PUT" -> {
                val req = new HttpPut(uri);
                copyBody(request, req);
                yield req;
            }
            case "DELETE" -> new HttpDelete(uri);
            default ->
                    throw new IllegalArgumentException("Request proxying for " + request.getMethod() + " is " +
                                                               "unsupported");
        };
        copyHeaders(request, proxyRequest);
        copyCookies(request, proxyRequest);
        return proxyRequest;
    }

    private static void copyBody(ContainerRequest request, BasicClassicHttpRequest req) {
        req.setEntity(new StringEntity(request.readEntity(String.class)));
    }

    private static void copyCookies(ContainerRequest request, HttpRequest proxyRequest) {
        request.getCookies().forEach((name, cookie) -> proxyRequest.setHeader(HttpHeaders.COOKIE, name + "=" + cookie));
    }

    private static void copyHeaders(ContainerRequest request, HttpRequest proxyRequest) {
        request.getHeaders().forEach((name, values) -> {
            if (RESTRICTED_HEADERS.contains(name)) {
                log.trace("Ignoring header: {}", name);
            }
            else {
                values.forEach(value -> {
                    try {
                        proxyRequest.setHeader(name, value);
                    }
                    catch (IllegalArgumentException e) {
                        log.warn("Ignoring header " + name + " due to error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private static CloseableHttpClient buildClient() {
        val socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build();
        val connManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5));

        connManager.setDefaultSocketConfig(SocketConfig.custom()
                                                   .setTcpNoDelay(true)
                                                   .setSoTimeout(Timeout.of(Duration.ofSeconds(2)))
                                                   .build());
        // Validate connections after 10 sec of inactivity
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                                                       .setConnectTimeout(Timeout.of(Duration.ofSeconds(2)))
                                                       .setSocketTimeout(Timeout.of(Duration.ofSeconds(2)))
                                                       .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                                       .setTimeToLive(TimeValue.ofHours(1))
                                                       .build());
        val rc = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(2)))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(rc)
                .build();
    }
}
