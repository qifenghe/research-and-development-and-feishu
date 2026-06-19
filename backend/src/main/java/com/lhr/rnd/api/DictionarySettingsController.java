package com.lhr.rnd.api;

import com.lhr.rnd.service.DictionaryConfig;
import com.lhr.rnd.service.DictionaryItemRule;
import com.lhr.rnd.service.DictionarySettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/dictionaries")
public class DictionarySettingsController {
    private final DictionarySettingsService dictionarySettingsService;

    public DictionarySettingsController(DictionarySettingsService dictionarySettingsService) {
        this.dictionarySettingsService = dictionarySettingsService;
    }

    @GetMapping
    public ApiResponse<List<DictionaryConfig>> dictionaries() {
        return ApiResponse.success(dictionarySettingsService.dictionaries());
    }

    @GetMapping("/{category}")
    public ApiResponse<DictionaryConfig> dictionary(@PathVariable String category) {
        return ApiResponse.success(dictionarySettingsService.dictionary(category));
    }

    @PutMapping("/{category}")
    public ApiResponse<DictionaryConfig> saveDictionary(
            @PathVariable String category,
            @Valid @RequestBody SaveDictionaryItemsRequest request
    ) {
        var items = request.items().stream()
                .map(item -> new DictionaryItemRule(
                        item.itemCode(),
                        item.itemLabel(),
                        item.enabled(),
                        item.sortOrder(),
                        item.remark()
                ))
                .toList();
        return ApiResponse.success(dictionarySettingsService.replaceDictionary(category, items));
    }

    @PostMapping("/defaults/initialize")
    public ApiResponse<List<DictionaryConfig>> initializeDefaultDictionaries() {
        return ApiResponse.success(dictionarySettingsService.initializeDefaultDictionaries());
    }
}
