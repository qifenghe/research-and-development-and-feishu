package com.lhr.rnd.service;

import com.lhr.rnd.model.CustomerFeedback;
import com.lhr.rnd.model.ShipmentRecord;

public record ShipmentFeedbackResult(
        ShipmentRecord shipment,
        CustomerFeedback feedback
) {
}
