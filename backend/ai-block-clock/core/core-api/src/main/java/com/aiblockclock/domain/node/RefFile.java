package com.aiblockclock.domain.node;

public record RefFile(RefFileId id, String key, String filename, long size) {
    // 엔진이 받지 않는 형식(계약 v2.0 전에 붙인 이미지)은 실행에 싣지 않는다. 실어 보내면 실행이 공급자 탓으로 깨진다.
    public boolean acceptedByRuntime() {
        return RefFileFormat.isAccepted(filename);
    }
}
