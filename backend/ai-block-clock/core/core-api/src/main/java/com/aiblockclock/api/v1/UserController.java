package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.UserProfileResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserService;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {
    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ApiResult<UserProfileResponse> getMe(@CurrentUser UserId userId) {
        return ApiResult.success(UserProfileResponse.from(userService.getProfile(userId)));
    }
}
