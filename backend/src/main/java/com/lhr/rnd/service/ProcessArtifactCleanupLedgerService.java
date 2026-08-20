package com.lhr.rnd.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Durable retry queue for final artifact bytes that never acquired READY metadata. */
@Service
public class ProcessArtifactCleanupLedgerService {
    private final JdbcTemplate jdbc;
    private final LocalArchiveStorageService storage;

    public ProcessArtifactCleanupLedgerService(JdbcTemplate jdbc, LocalArchiveStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(String key) {
        requireKey(key);
        jdbc.update("insert into process_artifact_cleanup_ledger(storage_key, created_at, last_attempt, error) values (?,?,null,null)", key, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(String key) { jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ?", key); }

    /** Runtime reconciliation only retries reservations already proven orphaned by a failed delete. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileStale() {
        reconcile(jdbc.query("select storage_key from process_artifact_cleanup_ledger where error is not null",
                (rs, row) -> rs.getString(1)));
    }

    /** At application-ready time no request transaction can still own a reservation. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileOnStartup() {
        reconcile(jdbc.query("select storage_key from process_artifact_cleanup_ledger", (rs, row) -> rs.getString(1)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteOrRetainForRetry(String key) {
        if (!exists(key)) return;
        deleteOne(key);
    }

    private void reconcile(List<String> keys) {
        for (var key : keys) {
            if (!safe(key)) {
                recordError(key, "unsafe artifact cleanup key");
                continue;
            }
            var refs = jdbc.queryForObject("select count(*) from experiment_process_artifact where storage_key = ? and status = 'READY'", Integer.class, key);
            if (refs != null && refs > 0) {
                jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ?", key);
            } else {
                deleteOne(key);
            }
        }
    }

    private void deleteOne(String key) {
        try {
            storage.delete(key);
            jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ?", key);
        } catch (RuntimeException exception) {
            recordError(key, truncate(exception.getMessage()));
        }
    }

    private boolean exists(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from process_artifact_cleanup_ledger where storage_key = ?", Boolean.class, key));
    }

    private void recordError(String key, String error) {
        jdbc.update("update process_artifact_cleanup_ledger set last_attempt = ?, error = ? where storage_key = ?", LocalDateTime.now(), error, key);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "artifact cleanup failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private boolean safe(String key) {
        return key != null && key.startsWith("process-artifacts/") && key.length() > "process-artifacts/".length()
                && !key.contains("..") && key.indexOf('\\') < 0;
    }

    private void requireKey(String key) { if (!safe(key)) throw new IllegalArgumentException("unsafe artifact cleanup key"); }
}
