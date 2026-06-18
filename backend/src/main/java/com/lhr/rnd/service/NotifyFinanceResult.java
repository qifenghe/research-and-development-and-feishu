package com.lhr.rnd.service;

import com.lhr.rnd.model.FinanceNotification;
import com.lhr.rnd.model.PricingFileRecord;

public record NotifyFinanceResult(
        PricingFileRecord pricingFile,
        FinanceNotification notification
) {
}
