# 평가 기준 스키마 — 서비스 규격이 아니다

09-26 플랫폼 견본(카탈로그 11종)을 서비스에서 없앴다. 판매자는 노드마다 출력 JSON 예시(`output.example`)를 준다.

이 폴더의 JSON Schema 는 그때의 견본 정의를 **준수율 평가 기준**으로만 남긴 것이다. 선택지(enum) · 개수 상한까지 보므로 서비스(예시의 키 · 타입만 검사)보다 엄격하다.

- 쓰는 곳: `ai/demo/check_compliance_brand.py`(시연 프롬프트 품질), `ai/demo/prepare_brand_demo.py`(추천 사전 평가 자료)
- 쓰지 않는 곳: Runtime(`ai/spike/linear-mvp`) — 여기서 읽지 않는다
