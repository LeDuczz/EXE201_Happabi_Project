package com.minduc.happabi.service.notification;

import com.corundumstudio.socketio.SocketIOServer;
import com.minduc.happabi.dto.response.notification.RealtimeNotificationPayload;
import com.minduc.happabi.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import java.net.BindException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(SocketIOServer.class)
public class RealtimeNotificationService {

    private static final String NOTIFICATION_EVENT = "notification";
    private static final String USER_ROOM_PREFIX = "user:";

    private final SocketIOServer socketIoServer;
    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private boolean started;

    @PostConstruct
    public void start() {
        socketIoServer.addConnectListener(client -> {
            String token = client.getHandshakeData().getSingleUrlParam("token");
            if (token == null || token.isBlank()) {
                client.disconnect();
                return;
            }

            try {
                Jwt jwt = jwtDecoder.decode(stripBearerPrefix(token));
                String userId = userRepository.findByCognitoSub(jwt.getSubject())
                        .map(user -> user.getId().toString())
                        .orElse(jwt.getSubject());
                client.joinRoom(room(userId));
                log.info("[Socket] Client connected userRoom={}", room(userId));
            } catch (RuntimeException e) {
                log.warn("[Socket] Invalid socket token: {}", e.getMessage());
                client.disconnect();
            }
        });

        socketIoServer.addDisconnectListener(client ->
                log.debug("[Socket] Client disconnected sessionId={}", client.getSessionId()));

        try {
            socketIoServer.start();
            started = true;
            log.info("[Socket] Socket.IO notification server started.");
        } catch (RuntimeException e) {
            if (hasCause(e, BindException.class)) {
                log.warn("[Socket] Socket.IO notification server was not started because the configured port is already in use.");
                return;
            }
            throw e;
        }
    }

    @PreDestroy
    public void stop() {
        if (started) {
            socketIoServer.stop();
        }
    }

    public void pushToUser(UUID userId, RealtimeNotificationPayload payload) {
        if (!started) {
            log.debug("[Socket] Skip realtime push because Socket.IO server is not started.");
            return;
        }
        socketIoServer.getRoomOperations(room(userId.toString()))
                .sendEvent(NOTIFICATION_EVENT, payload);
    }

    private String room(String userId) {
        return USER_ROOM_PREFIX + userId;
    }

    private String stripBearerPrefix(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
