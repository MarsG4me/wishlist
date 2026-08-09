package de.marsg.wishlist.endpoints.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envelope of EVERY list endpoint.
 *
 * @param total  entities matching the query in the database
 * @param count  entities actually returned in this response
 * @param limit  page size that was applied (clamped to 1..100)
 * @param offset index of the first returned entity
 */
public record ReturnListDTO<T>(
        @JsonProperty("total") long total,
        @JsonProperty("count") int count,
        @JsonProperty("limit") int limit,
        @JsonProperty("offset") int offset,
        @JsonProperty("items") List<T> items) {

    public static <E, D> ReturnListDTO<D> of(Page<E> page, Function<E, D> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }

    /** For pages whose items need context the mapper alone cannot supply. */
    public static <E, D> ReturnListDTO<D> of(Page<E> page, List<D> items) {
        return new ReturnListDTO<>(page.getTotalElements(), items.size(), page.getPageable().getPageSize(),
                (int) page.getPageable().getOffset(), items);
    }
}
