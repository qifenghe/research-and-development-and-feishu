package com.lhr.rnd.api;

import com.lhr.rnd.service.SampleWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class PricingBasisControllerTest {
    @Test void refusesMissingSessionBeforeLoadingAnyPricingData() {
        var controller=new PricingBasisController(new SampleWorkflowService());
        assertThatThrownBy(()->controller.load("P",new MockHttpServletRequest())).isInstanceOf(BusinessException.class).hasMessageContaining("会话");
    }
}
