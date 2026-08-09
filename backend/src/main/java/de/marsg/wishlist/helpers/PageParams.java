package de.marsg.wishlist.helpers;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paging parameters of list endpoints. The requester picks the page size; values are
 * clamped rather than rejected, so an oversized limit yields {@value #MAX_LIMIT}
 * instead of an error.
 */
public final class PageParams {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 20;

    private PageParams() {
    }

    public static Pageable of(Integer limit, Integer offset, Sort sort) {
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        long start = offset == null ? 0 : Math.max(0, offset);
        return new OffsetPageable(start, size, sort);
    }

    /**
     * Offset-based pageable: page numbers cannot express an arbitrary offset, which
     * infinite scrolling needs when the page size changes mid-session.
     */
    private record OffsetPageable(long offset, int size, Sort sort) implements Pageable {

        @Override
        public int getPageNumber() {
            return (int) (offset / size);
        }

        @Override
        public int getPageSize() {
            return size;
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
            return new OffsetPageable(offset + size, size, sort);
        }

        @Override
        public Pageable previousOrFirst() {
            return hasPrevious() ? new OffsetPageable(offset - size, size, sort) : first();
        }

        @Override
        public Pageable first() {
            return new OffsetPageable(0, size, sort);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            return new OffsetPageable((long) pageNumber * size, size, sort);
        }

        @Override
        public boolean hasPrevious() {
            return offset >= size;
        }

        @Override
        public boolean isPaged() {
            return true;
        }
    }
}
