package io.github.doubletree.iam.provisioning.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import java.util.List;

public record ScimListResponse<T>(
        List<String> schemas,
        long totalResults,
        int startIndex,
        int itemsPerPage,
        @JsonProperty("Resources") List<T> resources) {

    public static <T> ScimListResponse<T> of(long total, int startIndex, int requestedCount, List<T> resources) {
        return new ScimListResponse<>(
                List.of(ScimSchemas.LIST_RESPONSE),
                total,
                startIndex,
                requestedCount == 0 ? 0 : resources.size(),
                requestedCount == 0 ? List.of() : List.copyOf(resources));
    }
}
