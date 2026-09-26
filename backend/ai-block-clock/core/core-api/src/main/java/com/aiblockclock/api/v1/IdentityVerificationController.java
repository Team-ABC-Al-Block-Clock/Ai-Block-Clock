package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.IdentityVerificationSubmitRequest;
import com.aiblockclock.api.v1.response.IdentityVerificationResponse;
import com.aiblockclock.domain.identity.IdentityVerificationService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/identity-verification")
@RequiredArgsConstructor
public class IdentityVerificationController implements IdentityVerificationApi {
    private final IdentityVerificationService identityVerificationService;

    // 관리자 심사를 거쳐야 끝나므로 받았다는 뜻의 202 로 답한다
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<IdentityVerificationResponse> submit(
            @CurrentUser UserId submitter,
            @Valid @RequestBody IdentityVerificationSubmitRequest request
    ) {
        return ApiResult.success(IdentityVerificationResponse.from(identityVerificationService.submit(
                submitter, request.legalName(), request.birthDate())));
    }

    @Override
    @GetMapping
    public ApiResult<IdentityVerificationResponse> getMine(@CurrentUser UserId submitter) {
        return ApiResult.success(IdentityVerificationResponse.from(identityVerificationService.readMine(submitter)));
    }
}
