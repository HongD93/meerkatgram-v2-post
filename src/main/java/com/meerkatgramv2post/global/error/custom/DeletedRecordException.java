package com.meerkatgramv2post.global.error.custom;

public class DeletedRecordException extends RuntimeException {
    public DeletedRecordException(String message) {
        super(message);
    }
}
