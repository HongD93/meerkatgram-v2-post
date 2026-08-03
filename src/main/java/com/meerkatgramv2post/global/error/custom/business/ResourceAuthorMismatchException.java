package com.meerkatgramv2post.global.error.custom.business;

import com.meerkatgramv2post.global.error.custom.BusinessException;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;

public class ResourceAuthorMismatchException extends BusinessException {
    public ResourceAuthorMismatchException(String message) {
        super(CustomResponseCode.RESOURCE_AUTHOR_MISMATCH_ERROR, message);
    }
}
