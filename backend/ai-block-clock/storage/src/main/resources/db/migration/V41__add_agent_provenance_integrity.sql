-- 원본 기록을 지금 내용 · 체인과 대조한 마지막 결과다 (CHN-003). 원본 증명을 조회할 때마다 덮어쓴다.
-- 버전 하나에 기록 하나라 이력 표를 따로 두지 않고 기록 줄에 붙인다. 확정 전(PENDING)이면 대조하지 않아 비어 있다.
-- integrity_mismatch_reason 은 MISMATCHED 일 때만 있다 (NOT_ON_CHAIN, OWNER_MISMATCH, METADATA_CHANGED, RECORD_MISMATCH).
alter table agent_provenance
    add column integrity_result varchar(20) null;

alter table agent_provenance
    add column integrity_mismatch_reason varchar(30) null;

alter table agent_provenance
    add column integrity_checked_at datetime(6) null;
