create table agent
(
    id                 varchar(36)   not null,
    owner_id           bigint        not null,
    name               varchar(40)   not null,
    summary            varchar(80)   not null,
    description        varchar(2000) not null,
    usage_example      varchar(500)  null,
    onboarding_message varchar(20)   null,
    thumbnail_url      varchar(500)  null,
    public_version_id  varchar(36)   null,
    public_version_no  int           null,
    created_at         datetime(6)   not null,
    updated_at         datetime(6)   not null,
    primary key (id)
);

create index idx_agent_owner_created on agent (owner_id, created_at, id);

create table agent_version
(
    id                 varchar(36)    not null,
    agent_id           varchar(36)    not null,
    version_no         int            not null,
    graph_id           varchar(36)    not null,
    source_workflow_id varchar(36)    not null,
    source_revision    bigint         not null,
    prompt_node_count  int            not null,
    base_cost          decimal(41, 6) not null,
    tier               varchar(20)    not null,
    publication_state  varchar(20)    not null,
    created_at         datetime(6)    not null,
    updated_at         datetime(6)    not null,
    primary key (id),
    constraint fk_agent_version_agent
        foreign key (agent_id) references agent (id)
);

create unique index uk_agent_version_no on agent_version (agent_id, version_no);

create table agent_version_node
(
    id               bigint         not null auto_increment,
    agent_version_id varchar(36)    not null,
    node_key         varchar(64)    not null,
    owner_id         bigint         not null,
    name             varchar(40)    not null,
    node_type        varchar(20)    not null,
    prompt           text           not null,
    model            varchar(40)    not null,
    output_kind      varchar(10)    not null default 'TEXT',
    output_example   text           null,
    base_cost        decimal(41, 6) not null,
    primary key (id),
    constraint fk_agent_version_node_version
        foreign key (agent_version_id) references agent_version (id)
);

create unique index uk_agent_version_node_key on agent_version_node (agent_version_id, node_key);

create table agent_version_node_ref_file
(
    id         varchar(36)  not null,
    version_id varchar(36)  not null,
    node_key   varchar(64)  not null,
    ordinal    int          not null,
    file_key   varchar(255) not null,
    filename   varchar(255) not null,
    file_size  bigint       not null,
    created_at datetime(6)  not null,
    primary key (id)
);

create unique index uk_agent_version_node_ref_file_order
    on agent_version_node_ref_file (version_id, node_key, ordinal);

-- 에이전트 등급(PAY-016 ~ 018)을 계산할 재료다. 에이전트마다 한 줄이고, 줄이 없는 에이전트는 아직 세지 않은 신규다.
-- successful_runs · supplier_failures 는 실행을 닫을 때 더한다. 자기 실행 · 플랫폼 탓 · mock 성공은 세지 않는다.
-- review_count · average_rating 은 리뷰 기능이 아직 없어 시연 시드(db/seed/agent-grade-demo.sql)로만 들어온다.
-- 지표(보정 평점 · 정규화 사용량 · 성공률)는 저장하지 않는다. 수식의 조정값이 바뀌어도 낡은 값이 남지 않게 이 재료에서 매번 계산한다.
-- measured_at 은 재료가 마지막으로 바뀐 때다. 지표는 이 시점의 값이다.
-- CHECK 는 결과가 NULL 이면 통과시키므로, 평가가 있는데 평점이 빈 줄을 막으려고 is not null 을 따로 적는다.
create table agent_grade_metric
(
    agent_id          varchar(36)   not null,
    successful_runs   bigint        not null,
    supplier_failures bigint        not null,
    review_count      int           not null,
    average_rating    decimal(3, 2) null,
    measured_at       datetime(6)   not null,
    primary key (agent_id),
    constraint ck_agent_grade_metric_counts
        check (successful_runs >= 0 and supplier_failures >= 0 and review_count >= 0),
    constraint ck_agent_grade_metric_rating
        check ((review_count = 0 and average_rating is null)
            or (review_count > 0 and average_rating is not null and average_rating between 1 and 5))
);

-- 에이전트에 종합 점수와 등급(PAY-019/020)을 매길 때마다 한 줄씩 남긴다. 한 번 적고 고치지 않는다.
-- 재료(successful_runs ~ measured_at)는 매긴 순간의 agent_grade_metric 값을 그대로 옮긴 것이다. 재료 표는 계속 바뀌므로
-- 여기에 두지 않으면 그때 왜 그 등급이었는지 다시 볼 수 없다. measured_at 은 재료가 마지막으로 바뀐 때이고, 신규면 비어 있다.
-- 지표(rating_score · usage_score · success_rate)도 매긴 순간의 수식으로 낸 값이다. 조정값이 바뀌어도 그때의 값이 남는다.
-- score_tier 는 점수만 보고 닿은 등급, tier 는 허들까지 본 확정 등급이다. 허들에 걸리면 tier 가 더 낮다.
-- 가격에 쓰는 등급(agent_version.tier)은 여기서 바꾸지 않는다. 재산정 배치(PAY-022)가 이 결과를 보고 바꾼다.
create table agent_grade_history
(
    id                bigint auto_increment primary key,
    agent_id          varchar(36)   not null,
    successful_runs   bigint        not null,
    supplier_failures bigint        not null,
    review_count      int           not null,
    average_rating    decimal(3, 2) null,
    measured_at       datetime(6)   null,
    rating_score      decimal(9, 6) not null,
    usage_score       decimal(9, 6) not null,
    success_rate      decimal(7, 6) not null,
    score             decimal(7, 4) not null,
    score_tier        varchar(20)   not null,
    tier              varchar(20)   not null,
    created_at        datetime(6)   not null,
    constraint ck_agent_grade_history_counts
        check (successful_runs >= 0 and supplier_failures >= 0 and review_count >= 0),
    constraint ck_agent_grade_history_rating
        check ((review_count = 0 and average_rating is null)
            or (review_count > 0 and average_rating is not null and average_rating between 1 and 5)),
    constraint ck_agent_grade_history_scores
        check (rating_score between 0 and 100 and usage_score between 0 and 100
            and success_rate between 0 and 1 and score between 0 and 100)
);

-- 배치는 에이전트마다 마지막 이력을 읽어, 같은 결과면 줄을 더하지 않는다. 그 조회가 쓰는 색인이다.
create index ix_agent_grade_history_agent on agent_grade_history (agent_id, id);

-- 재산정 배치(PAY-022)가 버전의 가격 등급(agent_version.tier)을 바꿀 때마다 한 줄씩 남긴다. 한 번 적고 고치지 않는다.
-- 등급은 에이전트에 하나지만 가격 칸은 버전마다 있어, 바뀐 버전마다 한 줄이다. 바뀌지 않은 버전은 남기지 않는다.
-- before_price · after_price 는 그 버전의 원가에 바뀌기 전 · 뒤 등급의 배율을 곱한 판매가다.
-- grade_history_id 는 이 등급을 매긴 근거인 agent_grade_history 의 줄이다. 그때의 재료 · 지표 · 점수는 그 줄에 있다.
-- created_at 이 바뀐 때다. 이미 접수한 실행은 접수 때 적은 배율(execution_charge_rate)을 쓰므로 이 시점 뒤 새 실행부터 새 가격이다.
create table agent_tier_change
(
    id               bigint auto_increment primary key,
    agent_id         varchar(36)    not null,
    agent_version_id varchar(36)    not null,
    grade_history_id bigint         not null,
    before_tier      varchar(20)    not null,
    after_tier       varchar(20)    not null,
    before_price     decimal(41, 6) not null,
    after_price      decimal(41, 6) not null,
    created_at       datetime(6)    not null,
    constraint ck_agent_tier_change_changed check (before_tier <> after_tier),
    constraint ck_agent_tier_change_prices check (before_price > 0 and after_price > 0)
);

-- 에이전트 버전마다 원작자와 메타데이터 해시를 SSAFY 체인의 등기부 토큰에 적은 기록이다 (CHN-001/002).
-- 버전을 공개할 때 PENDING 으로 한 줄을 만들고, 체인에 적힌 것을 확인하면 CONFIRMED 로 바꾸며 나머지 칸을 채운다.
-- 버전 하나에 한 줄뿐이라 agent_version_id 가 기본 키다. 같은 버전을 두 번 기록하지 않는다.
-- document_name 은 체인 쪽 열쇠다. ERC-1400 문서 이름이 bytes32 라 31자까지이고, 겹치면 남의 기록을 덮으므로 유일하다.
-- 적히기 전에는 몇 번이고 다시 보낸다. last_attempted_at 이 오래된 것부터 집어, 계속 실패하는 한 줄이 나머지를 막지 않는다.
-- integrity_ 칸들은 기록을 지금 내용 · 체인과 대조한 마지막 결과다 (CHN-003). 원본 증명을 조회할 때마다 덮어쓴다.
-- 버전 하나에 기록 하나라 이력 표를 따로 두지 않고 기록 줄에 붙인다. 확정 전(PENDING)이면 대조하지 않아 비어 있다.
-- integrity_mismatch_reason 은 MISMATCHED 일 때만 있다 (NOT_ON_CHAIN, OWNER_MISMATCH, METADATA_CHANGED, RECORD_MISMATCH).
create table agent_provenance
(
    agent_version_id          varchar(36)  not null,
    agent_id                  varchar(36)  not null,
    version_no                int          not null,
    document_name             varchar(31)  not null,
    status                    varchar(20)  not null,
    requested_at              datetime(6)  not null,
    last_attempted_at         datetime(6)  null,
    owner_wallet_address      varchar(42)  null,
    metadata_hash             varchar(66)  null,
    document_uri              varchar(200) null,
    contract_address          varchar(42)  null,
    transaction_hash          varchar(66)  null,
    block_number              bigint       null,
    confirmed_at              datetime(6)  null,
    integrity_result          varchar(20)  null,
    integrity_mismatch_reason varchar(30)  null,
    integrity_checked_at      datetime(6)  null,
    primary key (agent_version_id),
    constraint uk_agent_provenance_document_name unique (document_name)
);

create index idx_agent_provenance_agent_version on agent_provenance (agent_id, version_no);
create index idx_agent_provenance_status on agent_provenance (status);

create table agent_favorite
(
    id         bigint auto_increment primary key,
    user_id    bigint      not null,
    agent_id   varchar(36) not null,
    created_at datetime(6) not null,

    constraint uq_agent_favorite_user_agent unique (user_id, agent_id)
);

create index idx_agent_favorite_user_created on agent_favorite (user_id, created_at, id);

create index idx_agent_favorite_agent on agent_favorite (agent_id);
