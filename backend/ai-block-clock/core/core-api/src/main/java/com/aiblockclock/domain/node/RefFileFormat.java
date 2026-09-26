package com.aiblockclock.domain.node;

import java.util.Locale;
import java.util.Set;

// 실행 엔진이 받는 참고 자료 형식이다 (엔진 계약 v2.0 7.1 — pdf · txt · md · csv). 엔진처럼 확장자로 판정한다.
// 붙일 때(RefFileValidator)와 실행에 실어 보낼 때(RuntimeWorkflow)가 같은 규칙을 써야, 붙여 둔 자료 때문에 실행이
// REF_FILE_REJECTED(공급자 탓)로 깨지지 않는다.
public final class RefFileFormat {
    public static final Set<String> ACCEPTED_EXTENSIONS = Set.of(".pdf", ".txt", ".md", ".csv");

    private RefFileFormat() {
    }

    public static boolean isAccepted(String filename) {
        return ACCEPTED_EXTENSIONS.contains(extensionOf(filename));
    }

    private static String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');

        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
