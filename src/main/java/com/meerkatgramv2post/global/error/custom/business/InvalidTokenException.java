package com.meerkatgramv2post.global.error.custom.business;

import com.meerkatgramv2post.global.error.custom.BusinessException;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;

public class InvalidTokenException extends BusinessException {
    public InvalidTokenException(String message) {
        super(CustomResponseCode.INVALID_TOKEN_ERROR, message);
    }
}
