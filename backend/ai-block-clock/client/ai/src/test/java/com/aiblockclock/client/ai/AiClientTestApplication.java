package com.aiblockclock.client.ai;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// :client:ai 만으로 컨텍스트를 띄우는 테스트의 기준점이다. core-api 의 애플리케이션이 이 패키지를 훑는 것과 같게,
// 이 모듈의 설정(AiClientConfiguration 등)을 훑어 올린다. 실행 가능한 애플리케이션은 core-api 하나뿐이다.
@SpringBootApplication
public class AiClientTestApplication {
}
