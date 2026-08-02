package com.smartwork.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope. Spring Data's own {@code Page} serialization is not a
 * contract we want to expose - it changes between versions and leaks repository concepts.
 */
@Schema(name = "PageResponse")
public record PageResponse<T>(
        List<T> content,
        PageMetadata page,
        List<SortField> sort) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public record SortField(String field, String direction) {
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<SortField> sortFields = page.getSort().stream()
                .map(order -> new SortField(order.getProperty(), order.getDirection().name()))
                .toList();

        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new PageMetadata(page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages()),
                sortFields);
    }

    public static <T> PageResponse<T> of(List<T> content) {
        return new PageResponse<>(
                content,
                new PageMetadata(0, content.size(), content.size(), 1),
                List.of(new SortField("none", Sort.Direction.ASC.name())));
    }
}
