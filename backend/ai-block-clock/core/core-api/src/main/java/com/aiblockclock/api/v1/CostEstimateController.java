package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.CostEstimateRequest;
import com.aiblockclock.api.v1.response.CostEstimateResponse;
import com.aiblockclock.domain.execution.CostEstimateService;
import com.aiblockclock.domain.execution.CostQuote;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class CostEstimateController implements CostEstimateApi {
    private final CostEstimateService costEstimateService;

    @Override
    @PostMapping("/cost-estimates")
    public ApiResult<CostEstimateResponse> estimate(
            @CurrentUser UserId requester,
            @RequestBody CostEstimateRequest request
    ) {
        CostQuote quote = costEstimateService.quote(WorkflowId.of(request.workflowId()), requester);

        return ApiResult.success(CostEstimateResponse.from(quote));
    }
}
