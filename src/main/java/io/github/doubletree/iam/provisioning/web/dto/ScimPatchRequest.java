package io.github.doubletree.iam.provisioning.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ScimPatchRequest(
        @NotEmpty List<String> schemas,
        @JsonProperty("Operations") @NotEmpty List<@Valid Operation> operations) {

    public record Operation(@NotBlank String op, String path, JsonNode value) {
    }
}
