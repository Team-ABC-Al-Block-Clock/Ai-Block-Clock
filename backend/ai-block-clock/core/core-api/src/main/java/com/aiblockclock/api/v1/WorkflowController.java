package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.EdgePlacementRequest;
import com.aiblockclock.api.v1.request.NodePlacementRequest;
import com.aiblockclock.api.v1.request.WorkflowReplaceRequest;
import com.aiblockclock.api.v1.request.WorkflowSaveRequest;
import com.aiblockclock.api.v1.response.WorkflowDeleteResponse;
import com.aiblockclock.api.v1.response.WorkflowDetailResponse;
import com.aiblockclock.api.v1.response.WorkflowPageResponse;
import com.aiblockclock.api.v1.response.WorkflowSaveResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowDetail;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.domain.workflow.WorkflowSummary;
import com.aiblockclock.enums.workflow.WorkflowSort;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController implements WorkflowApi {
    private final WorkflowService workflowService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<WorkflowSaveResponse> save(
            @CurrentUser UserId owner,
            @RequestBody WorkflowSaveRequest request
    ) {
        List<NodePlacement> nodes = NodePlacementRequest.toPlacements(request.nodes());
        List<GraphEdge> edges = EdgePlacementRequest.toEdges(request.edges());

        Workflow saved = workflowService.save(
                owner, request.name(), request.description(), nodes, edges);

        return ApiResult.success(WorkflowSaveResponse.from(saved));
    }

    @Override
    @GetMapping("/{workflowId}")
    public ApiResult<WorkflowDetailResponse> get(
            @CurrentUser UserId owner,
            @PathVariable UUID workflowId
    ) {
        WorkflowDetail detail = workflowService.getDetail(WorkflowId.of(workflowId), owner);

        return ApiResult.success(WorkflowDetailResponse.from(detail));
    }

    @Override
    @PutMapping("/{workflowId}")
    public ApiResult<WorkflowSaveResponse> replace(
            @CurrentUser UserId owner,
            @PathVariable UUID workflowId,
            @RequestBody WorkflowReplaceRequest request
    ) {
        List<NodePlacement> nodes = NodePlacementRequest.toPlacements(request.nodes());
        List<GraphEdge> edges = EdgePlacementRequest.toEdges(request.edges());
        Revision expectedRevision = Revision.of(request.revision());

        Workflow replaced = workflowService.replace(
                WorkflowId.of(workflowId), owner, request.name(), expectedRevision, nodes, edges);

        return ApiResult.success(WorkflowSaveResponse.from(replaced));
    }

    @Override
    @DeleteMapping("/{workflowId}")
    public ApiResult<WorkflowDeleteResponse> delete(
            @CurrentUser UserId owner,
            @PathVariable UUID workflowId
    ) {
        workflowService.delete(WorkflowId.of(workflowId), owner);

        return ApiResult.success(WorkflowDeleteResponse.of(workflowId, Instant.now()));
    }

    @Override
    @GetMapping
    public ApiResult<WorkflowPageResponse> findMine(
            @CurrentUser UserId owner,
            @RequestParam(defaultValue = "LAST_EXECUTED") WorkflowSort sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        CursorPage<WorkflowSummary> page = workflowService.findMine(owner, sort, keyword, cursor, size);

        return ApiResult.success(WorkflowPageResponse.from(page));
    }
}
