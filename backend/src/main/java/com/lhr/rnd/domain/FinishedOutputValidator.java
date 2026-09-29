package com.lhr.rnd.domain;

import java.math.BigDecimal;
import java.util.Set;
import com.lhr.rnd.api.BusinessException;

public final class FinishedOutputValidator {
    private FinishedOutputValidator() {}
    public static BigDecimal validate(BigDecimal weightKg, BigDecimal quantity, String unit) {
        if (!Set.of("袋","盒","份","个","盘","kg").contains(unit))
            throw new BusinessException("FINISHED_OUTPUT_UNIT_INVALID","成品单位不受支持");
        if ("kg".equals(unit)) {
            if ((weightKg == null) != (quantity == null) || (weightKg != null && weightKg.compareTo(quantity) != 0))
                throw new BusinessException("FINISHED_OUTPUT_QUANTITY_INVALID","kg 成品数量必须与成品重量一致");
            if (quantity != null && (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4 || quantity.precision()-quantity.scale()>10))
                throw new BusinessException("FINISHED_OUTPUT_QUANTITY_INVALID","kg 成品重量须为正数，最多四位小数");
        } else if (quantity != null) {
            try { if (quantity.intValueExact() <= 0) throw new ArithmeticException(); }
            catch (ArithmeticException e) {throw new BusinessException("FINISHED_OUTPUT_QUANTITY_INVALID","成品数量必须为正整数");}
        }
        return quantity;
    }
}
