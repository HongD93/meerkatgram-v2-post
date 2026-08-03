package com.meerkatgramv2post.global.response.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CustomResponseCode {
    SUCCESS(HttpStatus.OK, "00")
    // 인증 관련
    , NOT_REGISTERED_ERROR(HttpStatus.UNAUTHORIZED, "E01")
    , ALREADY_REGISTERED_ERROR(HttpStatus.CONFLICT, "E02")
    , UNAUTHENTICATED_ERROR(HttpStatus.UNAUTHORIZED, "E03")
    , UNAUTHORIZED_ERROR(HttpStatus.FORBIDDEN, "E04")
    , INVALID_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "E05")
    // Not Found Resource 관련
    , NOT_FOUND_RESOURCE_ERROR(HttpStatus.NOT_FOUND, "E10")
    , DUPLICATED_RESOURCE_ERROR(HttpStatus.CONFLICT, "E11")
    , RESOURCE_AUTHOR_MISMATCH_ERROR(HttpStatus.CONFLICT, "E12")
    // 유효성 검사 관련
    , INVALID_PARAMETER_ERROR(HttpStatus.BAD_REQUEST, "E21")
    // OAuth2 관련
    , OAUTH2_ERROR(HttpStatus.CONFLICT, "E30")
    , UNSUPPORTED_PROVIDER_ERROR(HttpStatus.CONFLICT, "E31")
    // File 관련
    , FILE_MANAGED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E40")
    // Not Found 관련
    , NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "E50")
    // DB 관련
    , DB_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E80")
    , DB_DUPLICATED_KEY_ERROR(HttpStatus.CONFLICT, "E81")
    // 시스템
    , SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E99")
    ;

    private final HttpStatus httpStatus;
    private final String code;

    CustomResponseCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
