package io.github.doubletree.iam.shared.web;

import io.github.doubletree.iam.shared.exception.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class SafePageRequest {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 100;

    private SafePageRequest() {
    }

    public static PageRequest of(int page, int size) {
        validate(page, size);
        return PageRequest.of(page, size);
    }

    public static PageRequest of(int page, int size, Sort sort) {
        validate(page, size);
        return PageRequest.of(page, size, sort);
    }

    private static void validate(int page, int size) {
        if (page < 0) {
            throw new ValidationException("Page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new ValidationException("Page size must be greater than or equal to 1");
        }
        if (size > MAX_SIZE) {
            throw new ValidationException("Page size must be less than or equal to " + MAX_SIZE);
        }
    }
}
