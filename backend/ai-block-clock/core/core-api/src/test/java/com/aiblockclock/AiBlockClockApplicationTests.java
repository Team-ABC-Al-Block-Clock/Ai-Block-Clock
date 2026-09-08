package com.aiblockclock;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 스프링 컨텍스트 전체를 띄우므로 통합 테스트로 분류한다.
// 태그가 없으면 MR 마다 도는 단위 테스트에 포함된다.
@Tag("integration")
@SpringBootTest
class AiBlockClockApplicationTests {

    @Test
    void contextLoads() {
    }

}
