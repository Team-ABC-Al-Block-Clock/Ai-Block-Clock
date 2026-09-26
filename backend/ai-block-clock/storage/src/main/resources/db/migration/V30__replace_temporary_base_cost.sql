-- 노드 원가 임시값 3 ABC(= 3 달러)를 기본 원가 0.0135 ABC 로 바로잡는다.
-- 3 ABC 는 원가를 잴 방법이 없던 때 모든 노드에 박은 값이고, 실측(gemini-2.5-pro 노드당 0.005 ~ 0.009 달러)의
-- 수백 배라 실행마다 예치가 너무 크게 묶였다. 0.0135 는 설정 node.base-cost.default-cost 의 기본값과 같다.
-- 에이전트 버전의 원가는 공개할 때 굳힌 값이라 고치지 않는 것이 규칙이지만, 이 임시값만은 한 번 바로잡는다.
-- 3 ABC 가 아닌 원가(시연용으로 손으로 넣은 값 등)는 건드리지 않는다.
update node_definition
   set base_cost = 0.013500
 where base_cost = 3.000000;

update agent_version_node
   set base_cost = 0.013500
 where base_cost = 3.000000;

-- 버전의 원가는 안에 놓인 노드 원가의 합이다. 모든 노드가 3 ABC 였던 버전만 노드 수 × 0.0135 로 다시 적는다.
update agent_version
   set base_cost = 0.013500 * prompt_node_count
 where base_cost = 3.000000 * prompt_node_count;
