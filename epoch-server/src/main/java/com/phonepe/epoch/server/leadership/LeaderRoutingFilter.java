package com.phonepe.epoch.server.leadership;

import com.phonepe.epoch.server.managed.LeadershipManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Proxies requests to leader
 */
@Slf4j
@Provider
@Singleton
@Priority(Priorities.USER)
@PreMatching
public class LeaderRoutingFilter implements ContainerRequestFilter {
    private static final Set<String> RESTRICTED_HEADERS = new TreeSet<>((lhs, rhs) -> lhs.equalsIgnoreCase(rhs) ? 0 : lhs.compareTo(rhs));

    static {
        RESTRICTED_HEADERS.add("Connection");
        RESTRICTED_HEADERS.add("Host");
        RESTRICTED_HEADERS.add("Content-Length");
        RESTRICTED_HEADERS.add("Accept-Encoding");
        RESTRICTED_HEADERS.add("Transfer-Encoding");
        RESTRICTED_HEADERS.add("Vary");
    }

    private final LeadershipManager manager;
    private final HttpClient httpClient;

    @Inject
    public LeaderRoutingFilter(LeadershipManager manager) {
        this.manager = manager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (manager.isLeader()) {
            log.trace("Allowing request as this node is the leader");
            return;
        }
        val leader = manager.leader().orElse(null);
        if(null == leader) {
            requestContext.abortWith(Response.serverError().build());
            return;
        }
        val parts = leader.replaceAll("/", "").split(":");

        val uri = requestContext.getUriInfo().getRequestUriBuilder()
                .scheme(parts[0])
                .host(parts[1])
                .port(Integer.parseInt(parts[2]))
                .build();
        val request = request(uri, (ContainerRequest)requestContext.getRequest());
        try {
            val proxyResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            val responseBuilder = Response.status(proxyResponse.statusCode())
                    .entity(proxyResponse.body());
            proxyResponse.headers().map().forEach((name, values) -> {
                if(RESTRICTED_HEADERS.contains(name)) {
                    log.trace("Ignoring header: {}", name);
                }
                else {
                    values.forEach(value -> responseBuilder.header(name, value));
                }
            });
            requestContext.abortWith(responseBuilder.build());
            return;
        }
        catch (InterruptedException e) {
            log.error("Error proxying request to " + uri + ": " + e.getMessage(), e);
            requestContext.abortWith(Response.seeOther(uri).build());
        }
        log.info("Routed to: {}", uri);
        requestContext.abortWith(Response.seeOther(uri).build());
    }

    private HttpRequest request(final URI uri, final ContainerRequest request) {
        val builder = HttpRequest.newBuilder(uri).method(request.getMethod(), getBodyPublisher(request));
        copyHeaders(request, builder);
        copyCookies(request, builder);
        return builder.build();
    }

    private static HttpRequest.BodyPublisher getBodyPublisher(ContainerRequest request) {
        return switch (request.getMethod()) {
            case "GET", "HEAD", "OPTIONS", "TRACE", "DELETE" -> HttpRequest.BodyPublishers.noBody();
            case "POST", "PUT", "PATCH" -> copyBody(request);
            default -> throw new IllegalArgumentException("Unsupported operation type: " + request.getMethod());
        };
    }

    private static HttpRequest.BodyPublisher copyBody(ContainerRequest request) {
        return HttpRequest.BodyPublishers.ofString(request.readEntity(String.class));
    }

    private static void copyCookies(ContainerRequest request, HttpRequest.Builder builder) {
        request.getCookies().forEach((name, cookie) -> builder.header(HttpHeaders.COOKIE, name + "=" + cookie));
    }

    private static void copyHeaders(ContainerRequest request, HttpRequest.Builder builder) {
        request.getHeaders().forEach((name, values) -> {
            if(RESTRICTED_HEADERS.contains(name)) {
                log.trace("Ignoring header: {}", name);
            }
            else {
                values.forEach(value -> {
                    try {
                        builder.header(name, value);
                    }
                    catch (IllegalArgumentException e) {
                        log.warn("Ignoring header " + name + " due to error: " + e.getMessage());
                    }
                });
            }
        });
    }
}
