package com.lhr.rnd.model;

import java.util.List;

public record ShipmentDetailView(
        ShipmentRecord shipment,
        SampleVersion version,
        CustomerFeedback customerFeedback,
        List<DetailFieldGroup> fieldGroups,
        List<DetailAction> availableActions
) {
}
