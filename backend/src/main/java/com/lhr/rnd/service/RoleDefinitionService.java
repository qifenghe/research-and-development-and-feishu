package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.RoleDefinition;
import com.lhr.rnd.persistence.entity.RoleDefinitionEntity;
import com.lhr.rnd.persistence.repository.RoleDefinitionRepository;
import com.lhr.rnd.persistence.repository.UserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class RoleDefinitionService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final Clock clock;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final UserAccountRepository userAccountRepository;

    @Autowired
    public RoleDefinitionService(
            RoleDefinitionRepository roleDefinitionRepository,
            UserAccountRepository userAccountRepository
    ) {
        this(Clock.systemDefaultZone(), roleDefinitionRepository, userAccountRepository);
    }

    RoleDefinitionService(
            Clock clock,
            RoleDefinitionRepository roleDefinitionRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.clock = clock;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @PostConstruct
    @Transactional
    public void initializeDefaults() {
        var now = now();
        defaultRoles().forEach(role -> roleDefinitionRepository.findById(role.roleCode())
                .orElseGet(() -> roleDefinitionRepository.save(new RoleDefinitionEntity(
                        role.roleCode(), role.roleName(), role.description(), ACTIVE, true, now, now
                ))));
    }

    public List<RoleDefinition> roles() {
        return roleDefinitionRepository.findAllByOrderBySystemBuiltinDescRoleCodeAsc().stream()
                .map(RoleDefinitionEntity::toModel)
                .toList();
    }

    @Transactional
    public RoleDefinition create(String roleCode, String roleName, String description) {
        var normalizedCode = normalizeCode(roleCode);
        var normalizedName = normalizeName(roleName);
        if (roleDefinitionRepository.existsById(normalizedCode)) {
            throw new BusinessException("ROLE_CODE_DUPLICATED", "角色编码已存在");
        }
        if (roleDefinitionRepository.existsByRoleName(normalizedName)) {
            throw new BusinessException("ROLE_NAME_DUPLICATED", "角色名称已存在");
        }
        var now = now();
        return roleDefinitionRepository.save(new RoleDefinitionEntity(
                normalizedCode, normalizedName, blankToNull(description), ACTIVE, false, now, now
        )).toModel();
    }

    @Transactional
    public RoleDefinition update(String roleCode, String roleName, String description) {
        var role = findRole(roleCode);
        var normalizedName = normalizeName(roleName);
        if (!role.getRoleName().equals(normalizedName) && roleDefinitionRepository.existsByRoleName(normalizedName)) {
            throw new BusinessException("ROLE_NAME_DUPLICATED", "角色名称已存在");
        }
        role.update(normalizedName, blankToNull(description), now());
        return roleDefinitionRepository.save(role).toModel();
    }

    @Transactional
    public RoleDefinition enable(String roleCode) {
        var role = findRole(roleCode);
        role.updateStatus(ACTIVE, now());
        return roleDefinitionRepository.save(role).toModel();
    }

    @Transactional
    public RoleDefinition disable(String roleCode) {
        var role = findRole(roleCode);
        if (role.isSystemBuiltin()) {
            throw new BusinessException("ROLE_SYSTEM_BUILTIN_PROTECTED", "系统内置角色不能停用");
        }
        if (userAccountRepository.countByRoleAndStatus(role.getRoleCode(), ACTIVE) > 0) {
            throw new BusinessException("ROLE_IN_USE", "仍有启用账号使用该角色，不能停用");
        }
        role.updateStatus(INACTIVE, now());
        return roleDefinitionRepository.save(role).toModel();
    }

    public void assertAssignable(String roleCode) {
        var normalizedCode = normalizeCode(roleCode);
        var role = roleDefinitionRepository.findById(normalizedCode)
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "角色不存在"));
        if (!ACTIVE.equals(role.getStatus())) {
            throw new BusinessException("ROLE_INACTIVE", "该角色已停用，不能分配给账号");
        }
    }

    private RoleDefinitionEntity findRole(String roleCode) {
        return roleDefinitionRepository.findById(normalizeCode(roleCode))
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "角色不存在"));
    }

    private String normalizeCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException("ROLE_CODE_REQUIRED", "角色编码不能为空");
        }
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BusinessException("ROLE_NAME_REQUIRED", "角色名称不能为空");
        }
        return roleName.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private List<DefaultRole> defaultRoles() {
        return List.of(
                new DefaultRole("RND_ASSISTANT", "研发内勤", "录入样品需求、寄样与客户反馈"),
                new DefaultRole("RND_DIRECTOR", "研发总监", "审核需求、分配任务、查看测试并审核核价"),
                new DefaultRole("RND_ENGINEER", "研发人员", "执行打样并审核本人负责产品的核价"),
                new DefaultRole("TESTER", "内部测试", "填写内部品尝测试结果"),
                new DefaultRole("QA_TESTER", "品控测试", "填写品控测试结果"),
                new DefaultRole("FINANCE", "财务", "接收已审核移交的核价文件"),
                new DefaultRole("MANAGER", "管理层", "查看项目进度与归档资料"),
                new DefaultRole("SYSTEM_ADMIN", "超级管理员", "维护账号、角色、权限和系统配置")
        );
    }

    private record DefaultRole(String roleCode, String roleName, String description) {
    }
}
