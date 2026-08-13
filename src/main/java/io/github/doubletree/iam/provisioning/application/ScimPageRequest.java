package io.github.doubletree.iam.provisioning.application;

import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class ScimPageRequest implements Pageable {

    static final int DEFAULT_COUNT = 50;
    static final int MAX_COUNT = 100;

    private final long offset;
    private final int pageSize;
    private final Sort sort;

    private ScimPageRequest(long offset, int pageSize, Sort sort) {
        this.offset = offset;
        this.pageSize = pageSize;
        this.sort = sort;
    }

    static ScimPageRequest of(int startIndex, int count) {
        if (startIndex < 1) {
            throw ScimProtocolException.invalidValue("startIndex must be greater than or equal to 1");
        }
        if (count < 0) {
            throw ScimProtocolException.invalidValue("count must be greater than or equal to 0");
        }
        int effectiveCount = Math.min(Math.max(count, 1), MAX_COUNT);
        return new ScimPageRequest(startIndex - 1L, effectiveCount, Sort.by("createdAt").ascending().and(Sort.by("id")));
    }

    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / pageSize);
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new ScimPageRequest(offset + pageSize, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return offset == 0 ? first() : new ScimPageRequest(Math.max(0, offset - pageSize), pageSize, sort);
    }

    @Override
    public Pageable first() {
        return new ScimPageRequest(0, pageSize, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        return new ScimPageRequest((long) pageNumber * pageSize, pageSize, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
