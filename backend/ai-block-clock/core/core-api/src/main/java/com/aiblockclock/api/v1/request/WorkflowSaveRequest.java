package com.aiblockclock.api.v1.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkflowSaveRequest(
        @NotBlank @Size(max = 40) String name,
        @NotEmpty @Valid List<NodePlacementRequest> nodes,
        @NotNull @Valid List<EdgePlacementRequest> edges
) {
}
