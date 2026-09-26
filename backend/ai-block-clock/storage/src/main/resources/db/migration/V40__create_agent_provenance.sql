-- 에이전트 버전마다 원작자와 메타데이터 해시를 SSAFY 체인의 등기부 토큰에 적은 기록이다 (CHN-001/002).
-- 버전을 공개할 때 PENDING 으로 한 줄을 만들고, 체인에 적힌 것을 확인하면 CONFIRMED 로 바꾸며 나머지 칸을 채운다.
-- 버전 하나에 한 줄뿐이라 agent_version_id 가 기본 키다. 같은 버전을 두 번 기록하지 않는다.
-- document_name 은 체인 쪽 열쇠다. ERC-1400 문서 이름이 bytes32 라 31자까지이고, 겹치면 남의 기록을 덮으므로 유일하다.
-- 적히기 전에는 몇 번이고 다시 보낸다. last_attempted_at 이 오래된 것부터 집어, 계속 실패하는 한 줄이 나머지를 막지 않는다.
create table agent_provenance
(
    agent_version_id     varchar(36)  not null,
    agent_id             varchar(36)  not null,
    version_no           int          not null,
    document_name        varchar(31)  not null,
    status               varchar(20)  not null,
    requested_at         datetime(6)  not null,
    last_attempted_at    datetime(6)  null,
    owner_wallet_address varchar(42)  null,
    metadata_hash        varchar(66)  null,
    document_uri         varchar(200) null,
    contract_address     varchar(42)  null,
    transaction_hash     varchar(66)  null,
    block_number         bigint       null,
    confirmed_at         datetime(6)  null,
    primary key (agent_version_id),
    constraint uk_agent_provenance_document_name unique (document_name)
);

create index idx_agent_provenance_agent_version on agent_provenance (agent_id, version_no);
create index idx_agent_provenance_status on agent_provenance (status);
