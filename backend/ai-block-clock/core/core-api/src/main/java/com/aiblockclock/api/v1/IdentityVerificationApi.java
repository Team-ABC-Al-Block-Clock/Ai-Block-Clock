package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.IdentityVerificationSubmitRequest;
import com.aiblockclock.api.v1.response.IdentityVerificationResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "본인 확인", description = """
        정산금을 출금하기 전에 실명과 생년월일을 제출하고 관리자 승인을 받는다.
        지갑 로그인은 "이 지갑의 주인" 까지만 확인하므로, 돈이 밖으로 나가기 전에 그 뒤의 사람을 확인한다.
        승인받아야 출금을 신청할 수 있다.
        """)
public interface IdentityVerificationApi {
    @Operation(summary = "본인 확인 제출",
            description = """
                    처음이면 새로 내고, 반려됐으면 고친 정보로 다시 낸다. 둘 다 심사 대기(`SUBMITTED`)가 된다.
                    심사 중이거나 이미 승인됐으면 다시 낼 수 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨. 관리자 심사를 기다린다 (`SUBMITTED`)"),
            @ApiResponse(responseCode = "400", description = """
                    요청 본문이 올바르지 않다 (`COMMON_001`). 실명은 1 ~ 50자, 생년월일은 오늘 이전이어야 한다""",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "409", description = """
                    제출한 본인 확인을 심사하고 있다 (`USER_007`).
                    이미 승인됐다 (`USER_008`)""",
                    content = @Content)
    })
    ApiResult<IdentityVerificationResponse> submit(UserId submitter, @Valid IdentityVerificationSubmitRequest request);

    @Operation(summary = "내 본인 확인 조회", description = "심사 상태와, 반려됐으면 사유를 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "404", description = "아직 제출하지 않았다 (`USER_009`)", content = @Content)
    })
    ApiResult<IdentityVerificationResponse> getMine(UserId submitter);
}
