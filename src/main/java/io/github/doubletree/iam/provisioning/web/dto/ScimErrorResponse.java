package io.github.doubletree.iam.provisioning.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimErrorResponse(
        List<String> schemas,
        String status,
        String scimType,
        String detail) {

    public static ScimErrorResponse of(int status, String scimType, String detail) {
        return new ScimErrorResponse(List.of(ScimSchemas.ERROR), String.valueOf(status), scimType, detail);
    }
}
