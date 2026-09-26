-- 엔진에 보낸 단계마다 받을 값(원가 × 배율)이다. 엔진 계약 v2.0 에서 엔진이 ABC 금액을 주지 않게 되어,
-- 끝난 뒤 받을 값을 실측 × 배율이 아니라 접수할 때 정한 단가로 센다. 접수할 때 한 번 적고 고치지 않는다.
-- 이 칸이 생기기 전에 접수한 실행은 비어 있고, 그 단계에는 받을 값이 없다.
alter table execution_charge_rate
    add column unit_price decimal(41, 6) null;
