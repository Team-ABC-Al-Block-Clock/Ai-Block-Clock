package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentOwner;
import com.aiblockclock.api.v1.request.NodeSaveRequest;
import com.aiblockclock.api.v1.response.NodeDeleteResponse;
import com.aiblockclock.api.v1.response.NodeDetailResponse;
import com.aiblockclock.api.v1.response.NodePageResponse;
import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionService;
import com.aiblockclock.domain.node.NodeDefinitionSummary;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class NodeController implements NodeApi {
    private final NodeDefinitionService nodeService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<NodeDetailResponse> save(
            @CurrentOwner OwnerId owner,
            @RequestBody NodeSaveRequest request
    ) {
        NodeDefinition saved = nodeService.create(
                owner, request.name(), Prompt.of(request.prompt()), request.modelOrDefault());

        return ApiResult.success(NodeDetailResponse.from(saved));
    }

    @Override
    @GetMapping("/{nodeId}")
    public ApiResult<NodeDetailResponse> get(
            @CurrentOwner OwnerId owner,
            @PathVariable UUID nodeId
    ) {
        return ApiResult.success(NodeDetailResponse.from(nodeService.get(NodeDefinitionId.of(nodeId), owner)));
    }

    @Override
    @PutMapping("/{nodeId}")
    public ApiResult<NodeDetailResponse> update(
            @CurrentOwner OwnerId owner,
            @PathVariable UUID nodeId,
            @RequestBody NodeSaveRequest request
    ) {
        NodeDefinition updated = nodeService.update(
                NodeDefinitionId.of(nodeId), owner, request.name(), Prompt.of(request.prompt()),
                request.modelOrDefault());

        return ApiResult.success(NodeDetailResponse.from(updated));
    }

    @Override
    @DeleteMapping("/{nodeId}")
    public ApiResult<NodeDeleteResponse> delete(
            @CurrentOwner OwnerId owner,
            @PathVariable UUID nodeId
    ) {
        nodeService.delete(NodeDefinitionId.of(nodeId), owner);

        return ApiResult.success(NodeDeleteResponse.of(nodeId, Instant.now()));
    }

    @Override
    @GetMapping
    public ApiResult<NodePageResponse> findMine(
            @CurrentOwner OwnerId owner,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        CursorPage<NodeDefinitionSummary> page = nodeService.getMine(owner, keyword, cursor, size);

        return ApiResult.success(NodePageResponse.from(page));
    }
}
