package com.techpix.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID accountId, String message, Instant createdAt) {
        jdbc.sql("INSERT INTO notifications (id, account_id, message, created_at) VALUES (:id, :accountId, :message, :createdAt)")
                .param("id", UUID.randomUUID())
                .param("accountId", accountId)
                .param("message", message)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    public List<String> messagesOf(UUID accountId) {
        return jdbc.sql("SELECT message FROM notifications WHERE account_id = :accountId ORDER BY created_at")
                .param("accountId", accountId)
                .query(String.class)
                .list();
    }
}
