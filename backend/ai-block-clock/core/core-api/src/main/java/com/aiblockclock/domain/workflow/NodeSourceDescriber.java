package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 배치가 가리키는 원본을 {@link PlacedSource} 로 푸는 쪽. 종류마다 하나씩 있다.
 *
 * <p>{@link NodeSourceResolver} 가 쓰기 경로에서 원본을 잠그고 박는다면, 이쪽은 읽기 경로에서
 * 박힌 참조를 되돌려 읽는다. 자리 수만큼 질의하지 않도록 참조를 한꺼번에 받는다.
 */
public interface NodeSourceDescriber {
    SourceType sourceType();

    /** 못 찾은 참조는 결과에서 빠진다. 부르는 쪽이 {@link PlacedSource#unknown()} 으로 메운다. */
    Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references);
}
