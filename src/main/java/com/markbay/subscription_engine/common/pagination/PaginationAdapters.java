package com.markbay.subscription_engine.common.pagination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationAdapters {

    private PaginationAdapters() {
    }

    private static final int DEFAULT_PAGE = 1;
    private static final int MAX_PAGE_SIZE = 35;
    private static final long DEFAULT_BUTTON_SIZE = 5;

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private static final Sort RECENT_FIRST_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    public static long resolvePage(Long requestedPage) {
        long page = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        return Math.max(1, page);
    }

    public static long resolvePageSize(Long requestedSize) {
        long size = requestedSize == null ? MAX_PAGE_SIZE : requestedSize;

        if (size <= 0) {
            return MAX_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    public static Pageable createPageRequest(Long page, Long pageSize) {
        long oneBasedPage = resolvePage(page);
        long zeroBasedPage = oneBasedPage - 1;
        long size = resolvePageSize(pageSize);

        return PageRequest.of((int) zeroBasedPage, (int) size, DEFAULT_SORT);
    }

    public static Pageable createRecentFirstPageRequest(Long page, Long pageSize) {
        long oneBasedPage = resolvePage(page);
        long zeroBasedPage = oneBasedPage - 1;
        long size = resolvePageSize(pageSize);

        return PageRequest.of((int) zeroBasedPage, (int) size, RECENT_FIRST_SORT);
    }

    public static Pageable createUnsortedPageRequest(Long page, Long pageSize) {
        long oneBasedPage = resolvePage(page);
        long zeroBasedPage = oneBasedPage - 1;
        long size = resolvePageSize(pageSize);

        return PageRequest.of((int) zeroBasedPage, (int) size, Sort.unsorted());
    }

    public static PaginationMeta toMeta(Page<?> page) {
        long currentPage = page.getNumber() + 1;
        long pageSize = page.getSize();
        long totalRecords = page.getTotalElements();
        long totalPages = page.getTotalPages();
        long currentCount = page.getNumberOfElements();

        boolean empty = page.isEmpty();

        long from = 0;
        long to = 0;

        if (!empty) {
            from = (currentPage - 1) * pageSize + 1;
            to = from + currentCount - 1;
        }

        boolean hasNext = page.hasNext();
        boolean hasPrevious = page.hasPrevious();

        Long nextPage = hasNext ? currentPage + 1 : null;
        Long previousPage = hasPrevious ? currentPage - 1 : null;

        long lastShowingPage = computeLastShowingPage(
                currentPage,
                totalPages,
                DEFAULT_BUTTON_SIZE
        );

        return PaginationMeta.builder()
                .currentPage(currentPage)
                .pageSize(pageSize)
                .totalRecordCount(totalRecords)
                .totalPages(totalPages)
                .currentCount(currentCount)
                .fromRecord(from)
                .toRecord(to)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .empty(empty)
                .nextPage(nextPage)
                .previousPage(previousPage)
                .sort(String.valueOf(page.getSort()))
                .lastShowingPage(lastShowingPage)
                .build();
    }

    private static long computeLastShowingPage(
            long currentPage,
            long totalPages,
            long buttonSize
    ) {
        if (totalPages <= 0) {
            return 0;
        }

        long last = currentPage + buttonSize - 1;
        return Math.min(last, totalPages);
    }
}