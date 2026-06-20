package com.minduc.happabi.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "realtime.socket.enabled", havingValue = "true")
public class SocketIoConfig {

    @Value("${realtime.socket.host:0.0.0.0}")
    private String host;

    @Value("${realtime.socket.port:9093}")
    private int port;

    @Value("${management.endpoints.web.cors.allowed-origins}")
    private String allowedOrigin;

    @Bean
    public SocketIOServer socketIoServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin(allowedOrigin);
        return new SocketIOServer(config);
    }
}
