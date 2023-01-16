package com.phonepe.epoch.server.managed;

import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.transport.httpcomponent.DroveHttpComponentsTransport;
import com.phonepe.epoch.server.config.DroveConfig;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Order(20)
@Singleton
public class DroveClientManager implements Managed {

    @Getter
    private final DroveConfig droveConfig;

    @Getter
    private final DroveClient client;

    @Inject
    public DroveClientManager(DroveConfig droveConfig) {
        this.droveConfig = droveConfig;
        val config = new DroveClientConfig(droveConfig.getEndpoints(),
                                           EpochUtils.getOrDefault(droveConfig.getCheckInterval()),
                                           EpochUtils.getOrDefault(droveConfig.getConnectionTimeout()),
                                           EpochUtils.getOrDefault(droveConfig.getOperationTimeout()));
        this.client = new DroveClient(config,
                                      List.of(request -> request.headers()
                                              .putAll(Map.of("Content-Type", List.of("application/json"),
                                                             "Accept", List.of("application/json")))),
                                              new DroveHttpComponentsTransport(config));
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        this.client.close();
    }

}
