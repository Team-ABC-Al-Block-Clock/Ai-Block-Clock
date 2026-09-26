-- 모델을 부르지 않고 도는 mock 실행인지를 접수할 때 적는다. 엔진 계약 v2.0 에서 받을 값이 엔진 원가와 끊겨,
-- 엔진 응답의 mock 칸 하나에만 기대면 그 칸이 빠졌을 때 가짜 결과물에 값을 받게 된다.
-- 이 값으로 mock 실행은 전부 돌려주고 등급 재료로도 세지 않는다. 이 칸이 생기기 전 실행은 mock 이 아닌 것으로 둔다.
alter table execution
    add column mock boolean not null default false;
