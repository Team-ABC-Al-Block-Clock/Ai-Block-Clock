package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;

// 실행을 닫을 때 엔진에게서 들은 것과, 무엇이 실행을 닫았는지다. 닫기(finish)의 판정 · 환불 · 정산 · 근거가 모두 이것에서 나온다.
// run 은 엔진이 마지막으로 알린 상태다. 우리가 끊은 실행(STALLED · LOST · DEAD_LETTERED)은 끊기 전에 마지막으로 본 것이고,
// 한 번도 못 봤으면 비어 있다.
// raw 는 엔진이 준 바이트 그대로(노드 출력 포함)이고, 받지 못했으면 비어 있다. 근거로 남긴다.
// failureCode 는 dead letter 사유로 정한 실패 코드다. 다른 닫기에서는 비어 있다 — 판정이 run 이나 닫은 까닭에서 나온다.
public record EngineReport(ClosedBy closedBy, RunStatus run, String raw, String failureCode) {
    public enum ClosedBy {
        // 엔진이 끝(succeeded · failed)을 알렸다
        ENGINE,
        // 기한 안에 엔진이 아무 말도 하지 않아 우리가 끊었다
        STALLED,
        // 엔진이 실행을 잊었다(재시작 · 재배포로 404 RUN_NOT_FOUND). 다시 나타나지 않는다
        LOST,
        // 엔진에 넘기지 못했다
        UNDISPATCHED,
        // 브로커가 실행 맡기기를 dead letter 로 보냈다(큐 대기 한도 초과 · 재배달 한도 초과 등). 엔진에 원본을 묻지 않는다
        DEAD_LETTERED,
        // 넘긴 채 큐 대기 한도 + 유예가 지나도록 dead letter 가 오지 않아 DB 안전망이 닫았다. 엔진에 원본을 묻지 않는다
        QUEUE_OVERDUE
    }

    public static EngineReport reported(RunStatus run, String raw) {
        return new EngineReport(ClosedBy.ENGINE, run, raw, null);
    }

    public static EngineReport stalled(RunStatus lastSeen, String raw) {
        return new EngineReport(ClosedBy.STALLED, lastSeen, raw, null);
    }

    public static EngineReport lost(RunStatus lastSeen) {
        return new EngineReport(ClosedBy.LOST, lastSeen, null, null);
    }

    public static EngineReport undispatched() {
        return new EngineReport(ClosedBy.UNDISPATCHED, null, null, null);
    }

    // lastSeen 은 dead letter 전에 마지막으로 받은 snapshot 이다. 없으면 비어 있다. failureCode 는 사유로 정한 실패 코드다.
    public static EngineReport deadLettered(RunStatus lastSeen, String failureCode) {
        return new EngineReport(ClosedBy.DEAD_LETTERED, lastSeen, null, failureCode);
    }

    // 넘긴 뒤 큐에서 기다리다 dead letter 없이 사라진 실행이다. 런타임이 받은 적이 없어 마지막으로 본 상태가 없다.
    public static EngineReport queueOverdue() {
        return new EngineReport(ClosedBy.QUEUE_OVERDUE, null, null, null);
    }

    // 끊기 전에 엔진이 멈춰 있던 단계다. 근거에 "어디서 멎었나" 로 남긴다.
    public String currentStepId() {
        return run == null || run.progress() == null ? null : run.progress().currentNodeId();
    }
}
