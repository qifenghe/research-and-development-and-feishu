package com.lhr.rnd.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Durable retry queue for final artifact bytes that never acquired READY metadata. */
@Service
public class ProcessArtifactCleanupLedgerService {
    private static final String RESERVED = "RESERVED";
    private static final String ORPHANED = "ORPHANED";
    private static final Duration DEFAULT_LEASE = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final LocalArchiveStorageService storage;
    private final Clock clock;
    private final Duration leaseDuration;

    @Autowired
    public ProcessArtifactCleanupLedgerService(JdbcTemplate jdbc, LocalArchiveStorageService storage) {
        this(jdbc, storage, Clock.systemDefaultZone(), DEFAULT_LEASE);
    }

    ProcessArtifactCleanupLedgerService(
            JdbcTemplate jdbc,
            LocalArchiveStorageService storage,
            Clock clock,
            Duration leaseDuration
    ) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation register(String key) {
        requireKey(key);
        var now = now();
        var reservation = new Reservation(key, UUID.randomUUID().toString(), now.plus(leaseDuration));
        jdbc.update("insert into process_artifact_cleanup_ledger(storage_key, state, lease_until, owner_token, created_at, last_attempt, error) values (?,?,?,?,?,null,null)",
                key, RESERVED, reservation.leaseUntil(), reservation.ownerToken(), now);
        return reservation;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(Reservation reservation) {
        var now = now();
        var updated = jdbc.update("update process_artifact_cleanup_ledger set lease_until = ? where storage_key = ? and owner_token = ? and state = ? and lease_until > ?",
                now.plus(leaseDuration), reservation.storageKey(), reservation.ownerToken(), RESERVED, now);
        if (updated != 1) {
            throw new IllegalStateException("artifact cleanup reservation lease lost");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(Reservation reservation) {
        jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ? and owner_token = ?",
                reservation.storageKey(), reservation.ownerToken());
    }

    /** Runtime reconciliation retries explicit orphans and claims only reservations whose lease has expired. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileStale() {
        reconcileEligible();
    }

    /** Startup is only another reconciler; it never receives authority over an unexpired reservation. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileOnStartup() {
        reconcileEligible();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void orphanAndDelete(Reservation reservation) {
        jdbc.update("update process_artifact_cleanup_ledger set state = ?, lease_until = ?, last_attempt = null, error = null where storage_key = ? and owner_token = ?",
                ORPHANED, now(), reservation.storageKey(), reservation.ownerToken());
        reconcileOwned(reservation.storageKey(), reservation.ownerToken());
    }

    private void reconcileEligible() {
        var now = now();
        var rows = jdbc.query("select storage_key, owner_token, state from process_artifact_cleanup_ledger where state = ? or (state = ? and lease_until <= ?)",
                (rs, row) -> new LedgerRow(rs.getString(1), rs.getString(2), rs.getString(3)), ORPHANED, RESERVED, now);
        for (var row : rows) {
            if (RESERVED.equals(row.state())) {
                var claimed = jdbc.update("update process_artifact_cleanup_ledger set state = ?, last_attempt = null, error = null where storage_key = ? and owner_token = ? and state = ? and lease_until <= ?",
                        ORPHANED, row.storageKey(), row.ownerToken(), RESERVED, now);
                if (claimed != 1) continue;
            }
            reconcileOwned(row.storageKey(), row.ownerToken());
        }
    }

    private void reconcileOwned(String key, String ownerToken) {
        if (!ownsOrphan(key, ownerToken)) return;
        if (!safe(key)) {
            recordError(key, ownerToken, "unsafe artifact cleanup key");
            return;
        }
        var refs = jdbc.queryForObject("select count(*) from experiment_process_artifact where storage_key = ? and status = 'READY'", Integer.class, key);
        if (refs != null && refs > 0) {
            jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ? and owner_token = ? and state = ?",
                    key, ownerToken, ORPHANED);
        } else {
            deleteOne(key, ownerToken);
        }
    }

    private void deleteOne(String key, String ownerToken) {
        if (!ownsOrphanForUpdate(key, ownerToken)) return;
        try {
            storage.delete(key);
            jdbc.update("delete from process_artifact_cleanup_ledger where storage_key = ? and owner_token = ? and state = ?",
                    key, ownerToken, ORPHANED);
        } catch (RuntimeException exception) {
            recordError(key, ownerToken, truncate(exception.getMessage()));
        }
    }

    private boolean ownsOrphan(String key, String ownerToken) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*) > 0 from process_artifact_cleanup_ledger where storage_key = ? and owner_token = ? and state = ?",
                Boolean.class, key, ownerToken, ORPHANED));
    }

    private boolean ownsOrphanForUpdate(String key, String ownerToken) {
        return !jdbc.query(
                "select storage_key from process_artifact_cleanup_ledger where storage_key = ? and owner_token = ? and state = ? for update",
                (rs, row) -> rs.getString(1), key, ownerToken, ORPHANED).isEmpty();
    }

    private void recordError(String key, String ownerToken, String error) {
        jdbc.update("update process_artifact_cleanup_ledger set last_attempt = ?, error = ? where storage_key = ? and owner_token = ? and state = ?",
                now(), error, key, ownerToken, ORPHANED);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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

    public record Reservation(String storageKey, String ownerToken, LocalDateTime leaseUntil) { }

    private record LedgerRow(String storageKey, String ownerToken, String state) { }
}
