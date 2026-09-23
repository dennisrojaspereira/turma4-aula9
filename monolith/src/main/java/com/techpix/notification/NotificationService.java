package com.techpix.notification;

import com.techpix.notification.internal.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** No monólito, notificar é apenas gravar uma linha. Um envio real (push, e-mail) viria depois. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void notify(UUID accountId, String message) {
        repository.insert(accountId, message, Instant.now(clock));
    }

    public List<String> messagesOf(UUID accountId) {
        return repository.messagesOf(accountId);
    }
}
