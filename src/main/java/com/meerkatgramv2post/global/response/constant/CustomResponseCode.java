package com.meerkatgramv2post.global.response.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CustomResponseCode {
    SUCCESS(HttpStatus.OK, "00")
    ,NOT_REGISTERED_ERROR(HttpStatus.UNAUTHORIZED, "E01")
    , UNAUTHENTICATED_ERROR(HttpStatus.UNAUTHORIZED, "E02")
    , UNAUTHORIZED_ERROR(HttpStatus.FORBIDDEN, "E03")
    , RESOURCE_NOT_FOUND_ERROR(HttpStatus.GONE, "E10")
    , DUPLICATED_DATA_ERROR(HttpStatus.CONFLICT, "E11")
    , RESOURCE_AUTHOR_MISMATCH_ERROR(HttpStatus.CONFLICT, "E12")
    , INVALID_PARAMETER_ERROR(HttpStatus.BAD_REQUEST, "E21")
    , FILE_MANAGED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E40")
    , NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "E50")
    , DB_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E80")
    , SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E99")
    ;

    private final HttpStatus httpStatus;
    private final String code;

    CustomResponseCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
