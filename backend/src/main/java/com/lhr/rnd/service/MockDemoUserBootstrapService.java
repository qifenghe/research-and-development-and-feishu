package com.lhr.rnd.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MockDemoUserBootstrapService {
    private final Environment environment;
    private final FeishuIntegrationService feishuIntegrationService;

    public MockDemoUserBootstrapService(
            Environment environment,
            FeishuIntegrationService feishuIntegrationService
    ) {
        this.environment = environment;
        this.feishuIntegrationService = feishuIntegrationService;
    }

    @Transactional
    public List<DemoUserBinding> seedDemoUsers() {
        var bindings = new ArrayList<DemoUserBinding>();
        bindings.add(bindFromEnvOrDefault("FEISHU_DEMO_ASSISTANT", "RND_ASSISTANT", "赵内勤", "ou_demo_assistant", "研发部"));
        bindings.add(bindFromEnvOrDefault("FEISHU_DEMO_DIRECTOR", "RND_DIRECTOR", "赵总监", "ou_demo_director", "研发部"));
        bindings.add(bindFromEnvOrDefault("FEISHU_DEMO_ENGINEER", "RND_ENGINEER", "张研发", "ou_demo_engineer", "研发部"));
        bindings.add(bindFromEnvOrDefault("FEISHU_DEMO_TESTER", "TESTER", "李测试", "ou_demo_tester", "品控部"));
        bindings.add(bindFromEnvOrDefault("FEISHU_DEMO_FINANCE", "FINANCE", "钱财务", "ou_demo_finance", "财务部"));
        return bindings;
    }

    private DemoUserBinding bindFromEnvOrDefault(
            String prefix,
            String role,
            String fallbackName,
            String defaultUserId,
            String departmentName
    ) {
        var feishuUserId = environment.getProperty(prefix + "_USER_ID", "").trim();
        if (feishuUserId.isBlank() || feishuUserId.contains("xxx")) {
            feishuUserId = defaultUserId;
        }
        var name = environment.getProperty(prefix + "_NAME", fallbackName);
        var user = feishuIntegrationService.bindUser(new FeishuIntegrationService.BindFeishuUserCommand(
                name,
                feishuUserId,
                role,
                departmentName
        ));
        return new DemoUserBinding(user.name(), user.feishuUserId(), user.role(), user.departmentName());
    }

    public record DemoUserBinding(
            String name,
            String feishuUserId,
            String role,
            String departmentName
    ) {
    }
}
