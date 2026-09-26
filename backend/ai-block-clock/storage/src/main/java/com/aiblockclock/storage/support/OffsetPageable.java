package com.aiblockclock.storage.support;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Objects;

/**
 * offset 을 그대로 표현하는 {@link Pageable}.
 *
 * <p>{@code PageRequest} 는 offset 을 {@code page * size} 로만 표현할 수 있어서, offset 이 size 의
 * 배수가 아니면 나타내지 못한다. offset 기반 커서를 쓰는 쪽에서 {@code PageRequest.of(offset / size, size)}
 * 로 우회하면 페이징 도중 size 가 바뀔 때 행이 누락되거나 중복된다.
 *
 * <p>정렬은 쿼리의 {@code order by} 절이 갖고 있으므로 여기서는 {@link Sort#unsorted()} 다.
 */
public final class OffsetPageable implements Pageable {
    private final long offset;
    private final int limit;

    private OffsetPageable(long offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset 은 0 이상이어야 한다: " + offset);
        }

        if (limit < 1) {
            throw new IllegalArgumentException("limit 은 1 이상이어야 한다: " + limit);
        }

        this.offset = offset;
        this.limit = limit;
    }

    public static OffsetPageable of(long offset, int limit) {
        return new OffsetPageable(offset, limit);
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + limit, limit);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageable(offset - limit, limit) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, limit);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable((long) pageNumber * limit, limit);
    }

    @Override
    public boolean hasPrevious() {
        return offset >= limit;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OffsetPageable that
                && this.offset == that.offset
                && this.limit == that.limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, limit);
    }

    @Override
    public String toString() {
        return "OffsetPageable(offset=" + offset + ", limit=" + limit + ")";
    }
}
