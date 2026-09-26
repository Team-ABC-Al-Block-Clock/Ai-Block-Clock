package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.UserSignupRequest;
import com.aiblockclock.api.v1.response.FavoriteAgentPageResponse;
import com.aiblockclock.api.v1.response.UserProfileResponse;
import com.aiblockclock.domain.agent.AgentFavoriteService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserService;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {
    private final UserService userService;
    private final AgentFavoriteService agentFavoriteService;

    @Override
    @GetMapping("/me")
    public ApiResult<UserProfileResponse> getMe(@CurrentUser UserId userId) {
        return ApiResult.success(UserProfileResponse.from(userService.getProfile(userId)));
    }

    @Override
    @PostMapping(value = "/me/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<UserProfileResponse> signup(
            @CurrentUser UserId userId,
            @ModelAttribute UserSignupRequest request
    ) {
        return ApiResult.success(UserProfileResponse.from(userService.signup(userId, request.toSignup())));
    }

    @Override
    @GetMapping("/me/favorites")
    public ApiResult<FavoriteAgentPageResponse> getMyFavorites(
            @CurrentUser UserId userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "9") int size
    ) {
        return ApiResult.success(FavoriteAgentPageResponse.from(agentFavoriteService.find(userId, cursor, size)));
    }
}
