package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalArchiveStorageService {
    private static final Path ROOT = Path.of("target", "rnd-archive");

    public String store(String relativePath, byte[] content) {
        var target = ROOT.resolve(relativePath).normalize();
        if (!target.startsWith(ROOT)) {
            throw new BusinessException("ARCHIVE_PATH_ILLEGAL", "归档路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return target.toString();
        } catch (IOException exception) {
            throw new BusinessException("ARCHIVE_FILE_WRITE_FAILED", "归档文件写入失败");
        }
    }
}
