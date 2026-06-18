package com.lhr.rnd.persistence;

import com.lhr.rnd.persistence.entity.SampleRequestEntity;
import com.lhr.rnd.persistence.repository.SampleRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SampleRequestRepositoryTest {

    @Autowired
    private SampleRequestRepository repository;

    @Test
    void savesAndFindsSampleRequestBySampleNo() {
        var createdAt = LocalDateTime.of(2026, 6, 18, 23, 15);
        repository.save(new SampleRequestEntity(
                "REQ-9001",
                "YP202606189001",
                "500g香卤大肠头",
                "冷冻即热菜",
                "LHYC",
                "500g/袋",
                "研发内勤",
                "PENDING_REVIEW",
                createdAt
        ));

        var found = repository.findBySampleNo("YP202606189001");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().productName()).isEqualTo("500g香卤大肠头");
        assertThat(found.orElseThrow().createdAt()).isEqualTo(createdAt);
    }
}
