package com.lhr.rnd.api;

import com.lhr.rnd.model.PricingPackagingItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmPricingPackagingRequest(@NotEmpty @Valid List<PricingPackagingItem> items) {
}
