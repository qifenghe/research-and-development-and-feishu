package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.TemplateConfigItem;
import com.lhr.rnd.persistence.entity.TemplateConfigEntity;
import com.lhr.rnd.persistence.repository.TemplateConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateSettingsService {
    private final TemplateConfigRepository templateConfigRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public TemplateSettingsService(TemplateConfigRepository templateConfigRepository) {
        this.templateConfigRepository = templateConfigRepository;
    }

    public List<TemplateConfigItem> listByType(String templateType) {
        return templateConfigRepository.findByTemplateTypeOrderByUpdatedAtDesc(templateType).stream()
                .map(this::toItem)
                .toList();
    }

    public List<TemplateConfigItem> listAll() {
        return templateConfigRepository.findAll().stream().map(this::toItem).toList();
    }

    @Transactional
    public TemplateConfigItem update(
            String templateCode,
            String templateName,
            String filePath,
            String versionNo,
            String status
    ) {
        var entity = templateConfigRepository.findAll().stream()
                .filter(item -> item.getTemplateCode().equals(templateCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("TEMPLATE_NOT_FOUND", "模板不存在"));
        entity.update(templateName, filePath, versionNo, status, LocalDateTime.now(clock));
        return toItem(templateConfigRepository.save(entity));
    }

    private TemplateConfigItem toItem(TemplateConfigEntity entity) {
        return new TemplateConfigItem(
                entity.getId(),
                entity.getTemplateCode(),
                entity.getTemplateName(),
                entity.getTemplateType(),
                entity.getFilePath(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getUpdatedAt()
        );
    }
}
