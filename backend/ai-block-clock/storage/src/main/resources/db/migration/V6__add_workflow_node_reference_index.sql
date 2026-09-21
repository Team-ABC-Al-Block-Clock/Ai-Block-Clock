-- 노드 삭제는 node_definition 행에 배타 락을 쥔 채 이 컬럼으로 배치 여부를 확인한다.
-- 인덱스가 없으면 그 확인이 workflow_node 풀스캔이라 락 보유 시간이 테이블 크기에 비례한다.
create index idx_workflow_node_reference on workflow_node (reference_id);
