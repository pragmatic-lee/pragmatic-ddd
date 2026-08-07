package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 PageRequest 分页请求的参数与偏移量计算。
 *
 * @author wizard-lee
 */
class PageRequestTest {

    @Test
    void of_valid_returnsInstance() {
        PageRequest request = PageRequest.of(1, 10);
        assertThat(request.pageNumber()).isEqualTo(1);
        assertThat(request.pageSize()).isEqualTo(10);
    }

    @Test
    void offset_calculatedFromPageNumber() {
        assertThat(PageRequest.of(1, 10).offset()).isEqualTo(0);
        assertThat(PageRequest.of(2, 10).offset()).isEqualTo(10);
        assertThat(PageRequest.of(3, 20).offset()).isEqualTo(40);
    }

    @Test
    void of_pageNumberBelowOne_throws() {
        assertThatThrownBy(() -> PageRequest.of(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_pageSizeBelowOne_throws() {
        assertThatThrownBy(() -> PageRequest.of(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_pageSizeAboveMax_throws() {
        assertThatThrownBy(() -> PageRequest.of(1, 201))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
