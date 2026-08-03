package com.meerkatgramv2post.global.error.custom.business;

import com.meerkatgramv2post.global.error.custom.BusinessException;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;

public class DuplicatedResourceException extends BusinessException {
    public DuplicatedResourceException(String message) {
        super(CustomResponseCode.DUPLICATED_RESOURCE_ERROR, message);
    }
}
