package com.lhr.rnd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalArchiveStorageServiceTest {
    @TempDir Path root;
    @Test void storesInConfiguredPersistentRootAndReadsAfterRecreation() throws Exception {
        var storage = new LocalArchiveStorageService(root.toString());
        storage.store("formal/result.xlsx", new byte[]{1, 2, 3});
        assertThat(Files.readAllBytes(root.resolve("formal/result.xlsx"))).containsExactly(1, 2, 3);
        assertThat(new LocalArchiveStorageService(root.toString()).read("formal/result.xlsx")).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> storage.store("../outside", new byte[]{1})).hasMessage("归档路径不合法");
        storage.delete("formal/result.xlsx");
        assertThat(root.resolve("formal/result.xlsx")).doesNotExist();
    }
}
