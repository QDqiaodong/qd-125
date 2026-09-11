package com.bufferblock.config;

import com.bufferblock.dto.Result;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateKeyReturnsExplicitFailureAndNoData() {
        Result<Void> result = handler.handleDataIntegrityViolation(
                new DuplicateKeyException("Duplicate entry 'TRF-20300101-001' for key 'transfer_no'"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getData()).isNull();
        assertThat(result.getMessage()).contains("唯一数据冲突", "登记未完成");
    }

    @Test
    void genericIntegrityFailureReturnsExplicitFailureAndNoData() {
        Result<Void> result = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("not-null constraint violated"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getData()).isNull();
        assertThat(result.getMessage()).contains("数据完整性校验失败", "登记未完成");
    }
}
