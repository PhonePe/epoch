package com.phonepe.epoch.server.managed;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.epoch.server.TestUtils;
import com.phonepe.epoch.server.config.DroveConfig;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class DroveClientManagerTest {
    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();


    @Test
    @SneakyThrows
    void test() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val dc = new DroveClientManager(new DroveConfig()
                                                .setEndpoints(List.of(controller1.baseUrl(),
                                                                      controller2.baseUrl()))
                                                .setConnectionTimeout(Duration.seconds(3))
                                                .setOperationTimeout(Duration.seconds(3)));

        dc.start();
        TestUtils.waitUntil(() -> dc.getClient().leader().isPresent());
        assertTrue(dc.getClient().leader().isPresent());
        dc.stop();
    }
}