package com.aiblockclock.enums.execution;

// 실패한 실행의 책임이다. 엔진이 판정해 준다 (계약 8.5).
public enum ExecutionFault {
    SUPPLIER,
    CONSUMER,
    PLATFORM
}
