package com.meerkatgramv2post.domain.post.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "페이지네이션 Request DTO")
public record PostIndexRequestDTO(
    @Schema(description = "페이지 번호", example = "1", nullable = false, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "1이상 숫자만 허용합니다.")
    Long page,

    @Schema(description = "출력 개수", example = "1", nullable = false, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "1이상 숫자만 허용합니다.")
    Long limit
) {
    public PostIndexRequestDTO(Long page, Long limit) {
        this.page = (page != null && page > 0) ? page : 1;
        this.limit = (limit != null && limit > 0) ? limit : 6;
    }
}
