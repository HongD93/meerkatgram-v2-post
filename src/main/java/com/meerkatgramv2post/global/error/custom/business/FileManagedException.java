package com.meerkatgramv2post.global.error.custom.business;

import com.meerkatgramv2post.global.error.custom.BusinessException;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;

public class FileManagedException extends BusinessException {
    public FileManagedException(String message) {
        super(CustomResponseCode.FILE_MANAGED_ERROR, message);
    }
}
