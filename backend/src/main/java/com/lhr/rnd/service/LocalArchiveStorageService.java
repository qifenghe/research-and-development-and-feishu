package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

@Service
public class LocalArchiveStorageService {
    private static final Path ROOT = Path.of("target", "rnd-archive");

    public String store(String relativePath, byte[] content) {
        var target = ROOT.resolve(relativePath).normalize();
        if (!target.startsWith(ROOT)) {
            throw new BusinessException("ARCHIVE_PATH_ILLEGAL", "归档路径不合法");
        }
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The target was never exposed through metadata when a write fails.
                }
            }
            throw new BusinessException("ARCHIVE_FILE_WRITE_FAILED", "归档文件写入失败");
        }
    }

    public byte[] read(String relativePath) {
        var target = ROOT.resolve(relativePath).normalize();
        if (!target.startsWith(ROOT)) {
            throw new BusinessException("ARCHIVE_PATH_ILLEGAL", "归档路径不合法");
        }
        if (!Files.exists(target)) {
            throw new BusinessException("ARCHIVE_FILE_NOT_FOUND", "归档文件不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new BusinessException("ARCHIVE_FILE_READ_FAILED", "归档文件读取失败");
        }
    }

    /** Removes a server-generated archive key after a database transaction cannot retain its metadata. */
    public void delete(String relativePath) {
        var target = ROOT.resolve(relativePath).normalize();
        if (!target.startsWith(ROOT)) {
            throw new BusinessException("ARCHIVE_PATH_ILLEGAL", "归档路径不合法");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException("ARCHIVE_FILE_DELETE_FAILED", "归档文件清理失败");
        }
    }
}
