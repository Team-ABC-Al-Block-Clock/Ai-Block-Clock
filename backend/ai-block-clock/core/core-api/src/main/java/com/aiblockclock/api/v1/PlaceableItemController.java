package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.PlaceableItemPageResponse;
import com.aiblockclock.domain.agent.PlaceableItem;
import com.aiblockclock.domain.agent.PlaceableItemService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.PlaceableScope;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows/node-palette")
@RequiredArgsConstructor
public class PlaceableItemController implements PlaceableItemApi {
    private final PlaceableItemService placeableItemService;

    @Override
    @GetMapping
    public ApiResult<PlaceableItemPageResponse> find(
            @CurrentUser UserId requester,
            @RequestParam PlaceableScope scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorPage<PlaceableItem> page = placeableItemService.find(requester, scope, keyword, cursor, size);

        return ApiResult.success(PlaceableItemPageResponse.from(page, scope));
    }
}
