package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.NodeSaveRequest;
import com.aiblockclock.api.v1.request.NodeTestRunRequest;
import com.aiblockclock.api.v1.request.RefFileAttachRequest;
import com.aiblockclock.api.v1.response.NodeDeleteResponse;
import com.aiblockclock.api.v1.response.NodeDetailResponse;
import com.aiblockclock.api.v1.response.NodePageResponse;
import com.aiblockclock.api.v1.response.NodeTestRunResponse;
import com.aiblockclock.api.v1.response.RefFileResponse;
import com.aiblockclock.domain.execution.NodeTestRunService;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionService;
import com.aiblockclock.domain.node.NodeDefinitionSummary;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class NodeController implements NodeApi {
    private final NodeDefinitionService nodeService;
    private final NodeTestRunService nodeTestRunService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<NodeDetailResponse> save(
            @CurrentUser UserId owner,
            @RequestBody NodeSaveRequest request
    ) {
        NodeDefinition saved = nodeService.create(
                owner, request.name(), Prompt.of(request.prompt()), request.modelOrDefault());

        return ApiResult.success(NodeDetailResponse.from(saved, List.of()));
    }

    @Override
    @GetMapping("/{nodeId}")
    public ApiResult<NodeDetailResponse> get(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId
    ) {
        NodeDefinitionId id = NodeDefinitionId.of(nodeId);

        return ApiResult.success(NodeDetailResponse.from(
                nodeService.get(id, owner), nodeService.getRefFiles(id, owner)));
    }

    @Override
    @PutMapping("/{nodeId}")
    public ApiResult<NodeDetailResponse> update(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId,
            @RequestBody NodeSaveRequest request
    ) {
        NodeDefinition updated = nodeService.update(
                NodeDefinitionId.of(nodeId), owner, request.name(), Prompt.of(request.prompt()),
                request.modelOrDefault());

        return ApiResult.success(NodeDetailResponse.from(
                updated, nodeService.getRefFiles(NodeDefinitionId.of(nodeId), owner)));
    }

    @Override
    @DeleteMapping("/{nodeId}")
    public ApiResult<NodeDeleteResponse> delete(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId
    ) {
        nodeService.delete(NodeDefinitionId.of(nodeId), owner);

        return ApiResult.success(NodeDeleteResponse.of(nodeId, Instant.now()));
    }

    @Override
    @PostMapping(value = "/{nodeId}/ref-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<RefFileResponse> attachRefFile(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId,
            @Valid @ModelAttribute RefFileAttachRequest request
    ) {
        return ApiResult.success(RefFileResponse.from(nodeService.attachRefFile(
                NodeDefinitionId.of(nodeId), owner, request.uploadedFile())));
    }

    @Override
    @DeleteMapping("/{nodeId}/ref-files/{refFileId}")
    public ApiResult<Void> detachRefFile(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId,
            @PathVariable UUID refFileId
    ) {
        nodeService.detachRefFile(NodeDefinitionId.of(nodeId), owner, RefFileId.of(refFileId));

        return ApiResult.success(null);
    }

    @Override
    @PostMapping("/{nodeId}/test-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<NodeTestRunResponse> startTestRun(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId,
            @Valid @RequestBody(required = false) NodeTestRunRequest request
    ) {
        NodeTestRunRequest body = request == null ? new NodeTestRunRequest(null, null) : request;

        return ApiResult.success(NodeTestRunResponse.from(nodeTestRunService.start(
                NodeDefinitionId.of(nodeId), owner, body.comment(), body.mockOrDefault())));
    }

    @Override
    @GetMapping("/{nodeId}/test-runs/{testRunId}")
    public ApiResult<NodeTestRunResponse> getTestRun(
            @CurrentUser UserId owner,
            @PathVariable UUID nodeId,
            @PathVariable UUID testRunId
    ) {
        return ApiResult.success(NodeTestRunResponse.from(nodeTestRunService.follow(
                NodeDefinitionId.of(nodeId), NodeTestRunId.of(testRunId), owner)));
    }

    @Override
    @GetMapping
    public ApiResult<NodePageResponse> findMine(
            @CurrentUser UserId owner,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        CursorPage<NodeDefinitionSummary> page = nodeService.getMine(owner, keyword, cursor, size);

        return ApiResult.success(NodePageResponse.from(page));
    }
}
