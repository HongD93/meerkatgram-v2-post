package com.meerkatgramv2post.global.error.custom;

public class ResourceAuthorMismatchException extends RuntimeException {
    public ResourceAuthorMismatchException(String message) {
        super(message);
    }
}
