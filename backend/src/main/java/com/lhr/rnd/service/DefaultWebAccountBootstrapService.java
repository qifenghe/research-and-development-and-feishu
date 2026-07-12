package com.lhr.rnd.service;

import com.lhr.rnd.persistence.entity.UserAccountEntity;
import com.lhr.rnd.persistence.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultWebAccountBootstrapService {
    private final Clock clock;
    private final UserAccountRepository userAccountRepository;
    private final PasswordHashService passwordHashService;

    @Autowired
    public DefaultWebAccountBootstrapService(
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService
    ) {
        this(Clock.systemDefaultZone(), userAccountRepository, passwordHashService);
    }

    DefaultWebAccountBootstrapService(
            Clock clock,
            UserAccountRepository userAccountRepository,
            PasswordHashService passwordHashService
    ) {
        this.clock = clock;
        this.userAccountRepository = userAccountRepository;
        this.passwordHashService = passwordHashService;
    }

    @Transactional
    public List<UserAccountEntity> seedDefaultAccounts() {
        return defaultAccounts().stream()
                .map(this::seed)
                .toList();
    }

    private UserAccountEntity seed(DefaultAccount account) {
        return userAccountRepository.findByUsername(account.username())
                .orElseGet(() -> userAccountRepository.save(new UserAccountEntity(
                        nextId(),
                        account.username(),
                        passwordHashService.hash("123456"),
                        account.name(),
                        account.feishuUserId(),
                        account.role(),
                        account.departmentName(),
                        "ACTIVE",
                        null,
                        now(),
                        now()
                )));
    }

    private List<DefaultAccount> defaultAccounts() {
        return List.of(
                new DefaultAccount("rnd_assistant", "赵内勤", "ou_demo_assistant", "RND_ASSISTANT", "研发部"),
                new DefaultAccount("rnd_director", "赵总监", "ou_demo_director", "RND_DIRECTOR", "研发部"),
                new DefaultAccount("rnd_engineer", "张研发", "ou_demo_engineer", "RND_ENGINEER", "研发部"),
                new DefaultAccount("tester", "李测试", "ou_demo_tester", "TESTER", "品控部"),
                new DefaultAccount("finance", "钱财务", "ou_demo_finance", "FINANCE", "财务部"),
                new DefaultAccount("admin", "系统管理员", "ou_demo_admin", "SYSTEM_ADMIN", "信息部")
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextId() {
        return "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    private record DefaultAccount(
            String username,
            String name,
            String feishuUserId,
            String role,
            String departmentName
    ) {
    }
}
