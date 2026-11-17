package com.college.complaint.service;

import com.college.complaint.dto.NotificationView;
import com.college.complaint.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public NotificationService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public void create(UUID userId, UUID complaintId, String type, String message) {
        if (userId == null || complaintId == null || message == null || message.isBlank()) return;
        jdbc.update("INSERT INTO notifications (user_id, complaint_id, notification_type, message) VALUES (?, ?, ?, ?)",
                userId, complaintId, type, message.length() > 500 ? message.substring(0, 500) : message);
        EmailTarget target = jdbc.query("SELECT email, role FROM users WHERE user_id = ? AND is_active = TRUE",
                rs -> rs.next() ? new EmailTarget(rs.getString("email"), rs.getString("role")) : null, userId);
        if (target != null && ("STUDENT".equals(target.role())
                || ("FACULTY".equals(target.role()) && "ASSIGNMENT".equals(type)))) {
            events.publishEvent(new NotificationMailEvent(target.email(), message));
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(UUID userId) {
        return jdbc.query("""
                SELECT notification_id, complaint_id, notification_type, message, created_at, read_at
                FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50
                """, (rs, row) -> new NotificationView(
                rs.getObject("notification_id", UUID.class), rs.getObject("complaint_id", UUID.class),
                rs.getString("notification_type"), rs.getString("message"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("read_at"))), userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        int updated = jdbc.update("UPDATE notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE notification_id = ? AND user_id = ?",
                notificationId, userId);
        if (updated == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record EmailTarget(String email, String role) { }
}
