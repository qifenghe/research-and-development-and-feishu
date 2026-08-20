package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
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
import java.util.UUID;

/** Durable ownership and cleanup ledger for pricing workbook archive attempts. */
@Service
public class PricingArchiveCleanupLedgerService {
    private static final String RESERVED = "RESERVED";
    private static final String ORPHANED = "ORPHANED";
    private static final String SAFE_PREFIX = "pricing-archives/";
    private static final Duration DEFAULT_LEASE = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final LocalArchiveStorageService storage;
    private final Clock clock;
    private final Duration leaseDuration;

    @Autowired
    public PricingArchiveCleanupLedgerService(JdbcTemplate jdbc, LocalArchiveStorageService storage) {
        this(jdbc, storage, Clock.systemDefaultZone(), DEFAULT_LEASE);
    }

    PricingArchiveCleanupLedgerService(
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
    public Reservation register(String pricingFileId) {
        var rows = jdbc.query(
                "select sample_no, version_code from pricing_file where id = ?",
                (rs, row) -> new PricingPath(rs.getString(1), rs.getString(2)), pricingFileId);
        if (rows.isEmpty()) {
            throw new BusinessException("PRICING_FILE_NOT_FOUND", "核价文件不存在");
        }
        var source = rows.get(0);
        var storageKey = "%s%s/%s/pricing/%s/attempt-%s.xlsx".formatted(
                SAFE_PREFIX,
                segment(source.sampleNo()),
                segment(source.versionCode()),
                segment(pricingFileId),
                UUID.randomUUID()
        );
        return register(pricingFileId, storageKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation register(String pricingFileId, String storageKey) {
        requireKey(storageKey);
        var now = now();
        var reservation = new Reservation(
                pricingFileId,
                storageKey,
                UUID.randomUUID().toString(),
                now.plus(leaseDuration)
        );
        jdbc.update("insert into pricing_archive_cleanup_ledger(storage_key, pricing_file_id, state, lease_until, owner_token, created_at, last_attempt, error) values (?,?,?,?,?,?,null,null)",
                storageKey, pricingFileId, RESERVED, reservation.leaseUntil(), reservation.ownerToken(), now);
        return reservation;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(Reservation reservation) {
        var now = now();
        var updated = jdbc.update("update pricing_archive_cleanup_ledger set lease_until = ? where storage_key = ? and pricing_file_id = ? and owner_token = ? and state = ? and lease_until > ?",
                now.plus(leaseDuration), reservation.storageKey(), reservation.pricingFileId(),
                reservation.ownerToken(), RESERVED, now);
        if (updated != 1) {
            throw new IllegalStateException("pricing archive cleanup reservation lease lost");
        }
    }

    /** Joins packaging confirmation so the ownership row stays locked through commit or rollback. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockForCommit(Reservation reservation) {
        requireKey(reservation.storageKey());
        return !jdbc.query(
                "select storage_key from pricing_archive_cleanup_ledger where storage_key = ? and pricing_file_id = ? and owner_token = ? and state = ? for update",
                (rs, row) -> rs.getString(1), reservation.storageKey(), reservation.pricingFileId(),
                reservation.ownerToken(), RESERVED).isEmpty();
    }

    /** Clears only a committed reservation whose exact pricing/archive reference is durable. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(Reservation reservation) {
        var row = locked(reservation);
        if (row == null || !RESERVED.equals(row.state())) return;
        if (referenced(reservation.pricingFileId(), reservation.storageKey())) {
            deleteLedger(reservation.storageKey(), reservation.ownerToken(), RESERVED);
            return;
        }
        jdbc.update("update pricing_archive_cleanup_ledger set state = ?, lease_until = ?, last_attempt = null, error = null where storage_key = ? and pricing_file_id = ? and owner_token = ? and state = ?",
                ORPHANED, now(), reservation.storageKey(), reservation.pricingFileId(),
                reservation.ownerToken(), RESERVED);
        reconcileCandidate(reservation.storageKey(), reservation.ownerToken(), now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void orphanAndDelete(Reservation reservation) {
        jdbc.update("update pricing_archive_cleanup_ledger set state = ?, lease_until = ?, last_attempt = null, error = null where storage_key = ? and pricing_file_id = ? and owner_token = ?",
                ORPHANED, now(), reservation.storageKey(), reservation.pricingFileId(), reservation.ownerToken());
        reconcileCandidate(reservation.storageKey(), reservation.ownerToken(), now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileStale() {
        reconcileEligible();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileOnStartup() {
        reconcileEligible();
    }

    private void reconcileEligible() {
        var now = now();
        var candidates = jdbc.query(
                "select storage_key, owner_token from pricing_archive_cleanup_ledger where state = ? or (state = ? and lease_until <= ?)",
                (rs, row) -> new Candidate(rs.getString(1), rs.getString(2)), ORPHANED, RESERVED, now);
        for (var candidate : candidates) {
            reconcileCandidate(candidate.storageKey(), candidate.ownerToken(), now);
        }
    }

    private void reconcileCandidate(String storageKey, String ownerToken, LocalDateTime now) {
        var rows = jdbc.query(
                "select pricing_file_id, state, lease_until from pricing_archive_cleanup_ledger where storage_key = ? and owner_token = ? for update",
                (rs, row) -> new LedgerRow(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()),
                storageKey, ownerToken);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        if (RESERVED.equals(row.state())) {
            if (row.leaseUntil().isAfter(now)) return;
            jdbc.update("update pricing_archive_cleanup_ledger set state = ?, last_attempt = null, error = null where storage_key = ? and owner_token = ? and state = ?",
                    ORPHANED, storageKey, ownerToken, RESERVED);
        } else if (!ORPHANED.equals(row.state())) {
            return;
        }
        if (!safe(storageKey)) {
            recordError(storageKey, ownerToken, "unsafe pricing archive cleanup key");
            return;
        }
        if (referenced(row.pricingFileId(), storageKey)) {
            deleteLedger(storageKey, ownerToken, ORPHANED);
            return;
        }
        try {
            storage.delete(storageKey);
            deleteLedger(storageKey, ownerToken, ORPHANED);
        } catch (RuntimeException exception) {
            recordError(storageKey, ownerToken, truncate(exception.getMessage()));
        }
    }

    private LedgerRow locked(Reservation reservation) {
        var rows = jdbc.query(
                "select pricing_file_id, state, lease_until from pricing_archive_cleanup_ledger where storage_key = ? and pricing_file_id = ? and owner_token = ? for update",
                (rs, row) -> new LedgerRow(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()),
                reservation.storageKey(), reservation.pricingFileId(), reservation.ownerToken());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean referenced(String pricingFileId, String storageKey) {
        var count = jdbc.queryForObject(
                "select count(*) from archive_file archive join pricing_file pricing on pricing.id = archive.business_id where archive.business_type = 'PRICING_FILE' and archive.business_id = ? and archive.file_path = ? and archive.file_status = 'ARCHIVED'",
                Integer.class, pricingFileId, storageKey);
        return count != null && count > 0;
    }

    private void deleteLedger(String storageKey, String ownerToken, String state) {
        jdbc.update("delete from pricing_archive_cleanup_ledger where storage_key = ? and owner_token = ? and state = ?",
                storageKey, ownerToken, state);
    }

    private void recordError(String storageKey, String ownerToken, String error) {
        jdbc.update("update pricing_archive_cleanup_ledger set last_attempt = ?, error = ? where storage_key = ? and owner_token = ? and state = ?",
                now(), error, storageKey, ownerToken, ORPHANED);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "pricing archive cleanup failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private boolean safe(String storageKey) {
        return storageKey != null && storageKey.startsWith(SAFE_PREFIX)
                && storageKey.length() > SAFE_PREFIX.length()
                && !storageKey.contains("..") && storageKey.indexOf('\\') < 0;
    }

    private void requireKey(String storageKey) {
        if (!safe(storageKey)) throw new IllegalArgumentException("unsafe pricing archive cleanup key");
    }

    private String segment(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim().replaceAll("[^\\p{L}\\p{N}._-]", "_");
    }

    public record Reservation(
            String pricingFileId,
            String storageKey,
            String ownerToken,
            LocalDateTime leaseUntil
    ) { }

    private record Candidate(String storageKey, String ownerToken) { }
    private record LedgerRow(String pricingFileId, String state, LocalDateTime leaseUntil) { }
    private record PricingPath(String sampleNo, String versionCode) { }
}
