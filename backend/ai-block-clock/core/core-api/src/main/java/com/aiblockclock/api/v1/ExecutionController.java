package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.ExecutionStartRequest;
import com.aiblockclock.api.v1.response.ExecutionProgressResponse;
import com.aiblockclock.api.v1.response.ExecutionStartResponse;
import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionResult;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class ExecutionController implements ExecutionApi {
    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<ExecutionStartResponse> start(
            @CurrentUser UserId requester,
            @Valid @ModelAttribute ExecutionStartRequest request
    ) {
        Workflow workflow = workflowService.get(WorkflowId.of(request.workflowId()), requester);

        Execution started = executionService.start(requester, workflow,
                request.comment(), request.mockOrDefault(), request.uploadedFile());

        return ApiResult.success(ExecutionStartResponse.from(started));
    }

    @Override
    @GetMapping("/{executionId}")
    public ApiResult<ExecutionProgressResponse> follow(
            @CurrentUser UserId requester,
            @PathVariable UUID executionId
    ) {
        return ApiResult.success(ExecutionProgressResponse.from(
                executionService.follow(ExecutionId.of(executionId), requester)));
    }

    @Override
    @GetMapping("/{executionId}/result")
    public ResponseEntity<Resource> download(
            @CurrentUser UserId requester,
            @PathVariable UUID executionId,
            @RequestParam(required = false) ExecutionResultFormat format
    ) {
        ExecutionResultFormat wanted = format == null ? ExecutionResultFormat.defaultFormat() : format;
        ExecutionResult result = executionService.download(ExecutionId.of(executionId), requester, wanted);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(wanted.contentType()))
                .contentLength(result.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(wanted.downloadName(executionId.toString()))
                                .build()
                                .toString())
                .body(new FileSystemResource(executionService.locate(result)));
    }
}
