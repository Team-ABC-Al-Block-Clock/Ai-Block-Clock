-- ABC (AI Block Clock) — 스키마 전체
--
-- master 의 Flyway 마이그레이션(V1~V7)을 순서대로 이어 붙인 것이다.
-- 운영 DB 의 mysqldump 와 같은 스키마가 나온다 (MySQL 8.4).
-- 데이터가 필요하면 같은 폴더의 ai_block_clock_dump.sql 을 쓴다.
--
-- 생성: 2026-09-28 · 기준 커밋 24712169 (origin/master)

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- V1__create_account.sql
-- ============================================================
CREATE TABLE app_user
(
    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_address               VARCHAR(42) NOT NULL,
    nickname                     VARCHAR(20) NULL,
    introduction                 VARCHAR(40) NULL,
    terms_agreed_at              DATETIME(6) NULL,
    recovery_notice_confirmed_at DATETIME(6) NULL,
    onboarding_completed_at      DATETIME(6) NULL,
    version                      BIGINT      NOT NULL DEFAULT 0,
    created_at                   DATETIME(6) NOT NULL,
    updated_at                   DATETIME(6) NOT NULL,
    CONSTRAINT uq_app_user_wallet_address UNIQUE (wallet_address)
);

CREATE TABLE wallet_auth_message
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    nonce          VARCHAR(64) NOT NULL,
    message        TEXT        NOT NULL,
    issued_at      DATETIME(6) NOT NULL,
    expires_at     DATETIME(6) NOT NULL,
    used_at        DATETIME(6) NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_wallet_auth_message_wallet_address UNIQUE (wallet_address)
);

-- 출금 전에 받는 본인 확인이다(ACC-004). 사용자마다 한 줄이고, 반려되면 같은 줄을 고쳐 다시 제출한다.
-- submitted_at 은 마지막으로 제출한 시각이다. 관리자는 오래 기다린 것부터 본다.
-- reviewer_id · reviewed_at · reject_reason 은 관리자가 승인 · 반려한 뒤에만 있다. 다시 제출하면 비운다.
create table identity_verification
(
    id            bigint auto_increment primary key,
    user_id       bigint       not null,
    legal_name    varchar(50)  not null,
    birth_date    date         not null,
    status        varchar(20)  not null,
    submitted_at  datetime(6)  not null,
    reviewer_id   bigint       null,
    reviewed_at   datetime(6)  null,
    reject_reason varchar(200) null,
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    constraint uq_identity_verification_user unique (user_id)
);

create index idx_identity_verification_status on identity_verification (status, submitted_at);

-- ============================================================
-- V2__create_spring_session.sql
-- ============================================================
CREATE TABLE SPRING_SESSION
(
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100) NULL,
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES
(
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- ============================================================
-- V3__create_payment.sql
-- ============================================================
CREATE TABLE ledger_account
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_type VARCHAR(30)    NOT NULL,
    reference_id VARCHAR(100)   NOT NULL,
    balance      DECIMAL(41, 6) NOT NULL DEFAULT 0.000000,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_ledger_account_type_reference
        UNIQUE (account_type, reference_id)
);

CREATE TABLE ledger_transaction
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(30)  NOT NULL,
    idempotency_key  VARCHAR(150) NOT NULL,
    reference_type   VARCHAR(30)  NOT NULL,
    reference_id     VARCHAR(100) NOT NULL,
    chain_id         BIGINT       NULL,
    transaction_hash VARCHAR(66)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_ledger_transaction_idempotency
        UNIQUE (idempotency_key),
    CONSTRAINT uq_ledger_transaction_chain_hash
        UNIQUE (chain_id, transaction_hash)
);

CREATE TABLE ledger_entry
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_transaction_id BIGINT         NOT NULL,
    ledger_account_id     BIGINT         NOT NULL,
    amount_delta          DECIMAL(41, 6) NOT NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ledger_entry_transaction
        FOREIGN KEY (ledger_transaction_id)
            REFERENCES ledger_transaction (id),
    CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (ledger_account_id)
            REFERENCES ledger_account (id),
    CONSTRAINT ck_ledger_entry_amount_not_zero
        CHECK (amount_delta <> 0.000)
);

CREATE INDEX idx_ledger_entry_account
    ON ledger_entry (ledger_account_id);

CREATE INDEX idx_ledger_entry_transaction
    ON ledger_entry (ledger_transaction_id);

CREATE TABLE charge_request
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_reference_id        VARCHAR(100)   NOT NULL,
    sender_wallet_address    VARCHAR(42)    NOT NULL,
    chain_id                 BIGINT         NOT NULL,
    transaction_hash         VARCHAR(66)    NOT NULL,
    requested_deposit_amount DECIMAL(38, 6) NOT NULL,
    credited_abc_amount      DECIMAL(41, 6) NULL,
    status                   VARCHAR(30)    NOT NULL,
    status_reason            VARCHAR(50)    NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    confirmed_at             DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_charge_request_chain_hash
        UNIQUE (chain_id, transaction_hash),
    CONSTRAINT ck_charge_request_deposit_amount_positive
        CHECK (requested_deposit_amount > 0),
    CONSTRAINT ck_charge_request_abc_amount_positive
        CHECK (
            credited_abc_amount IS NULL
                OR credited_abc_amount > 0
            )
);

CREATE INDEX idx_charge_request_user_status
    ON charge_request (user_reference_id, status);

-- 공급자가 정산금을 자기 지갑으로 보내 달라고 한 출금 신청이다(PAY-023). 신청할 때 금액을 장부에서 묶는다(withdrawal-hold:{id}).
-- request_id 는 화면이 신청 버튼을 누를 때 만드는 번호다. 같은 사람이 같은 번호로 다시 보내면 새로 만들지 않는다.
-- wallet_address 는 신청할 때의 로그인 지갑을 옮겨 적는다. 지급(PAY-025)은 이 주소로 보낸다.
-- reviewer_id · reviewed_at · reject_reason 은 관리자가 승인 · 반려한 기록이다(PAY-024). 심사 전에는 비어 있다.
-- payout_* 는 승인된 출금을 보관 지갑에서 abcToken 으로 보낸 기록이다(PAY-025).
-- 서명한 거래와 그 해시를 보내기 전에 적는다. 보냈는지 모를 때 같은 거래를 그대로 다시 보내려고 원문도 둔다.
-- 서명된 거래는 체인에 퍼지면 누구나 볼 수 있는 값이라 비밀이 아니다. 한 거래가 두 출금에 쓰이지 않는다.
create table withdrawal
(
    id                      bigint auto_increment primary key,
    request_id              varchar(36)    not null,
    requester_id            bigint         not null,
    amount_abc              decimal(41, 6) not null,
    wallet_address          varchar(42)    not null,
    status                  varchar(20)    not null,
    reviewer_id             bigint         null,
    reviewed_at             datetime(6)    null,
    reject_reason           varchar(200)   null,
    payout_transaction_hash varchar(66)    null,
    payout_raw_transaction  text           null,
    payout_submitted_at     datetime(6)    null,
    paid_at                 datetime(6)    null,
    created_at              datetime(6)    not null,
    updated_at              datetime(6)    not null,
    constraint uq_withdrawal_requester_request unique (requester_id, request_id),
    constraint ck_withdrawal_amount_positive check (amount_abc > 0),
    constraint uq_withdrawal_payout_transaction_hash unique (payout_transaction_hash)
);

-- 처리 중인 출금이 있는지, 마지막 신청이 언제인지를 신청마다 사용자별로 본다.
create index idx_withdrawal_requester_status on withdrawal (requester_id, status);

-- 관리자가 심사 대기 목록을 오래된 신청부터 본다.
create index idx_withdrawal_status_created on withdrawal (status, created_at);

-- ============================================================
-- V4__create_node.sql
-- ============================================================
create table node_definition
(
    id             varchar(36)    not null,
    owner_id       bigint         not null,
    name           varchar(40)    not null,
    node_type      varchar(20)    not null default 'LLM',
    prompt         text           not null,
    model          varchar(40)    not null,
    output_kind    varchar(10)    not null default 'TEXT',
    output_example text           null,
    base_cost      decimal(41, 6) not null,
    created_at     datetime(6)    not null,
    updated_at     datetime(6)    not null,
    primary key (id)
);

create index idx_node_definition_owner_created on node_definition (owner_id, created_at, id);

create table node_ref_file
(
    id                 varchar(36)  not null,
    node_definition_id varchar(36)  not null,
    ordinal            int          not null,
    file_key           varchar(255) not null,
    filename           varchar(255) not null,
    file_size          bigint       not null,
    created_at         datetime(6)  not null,
    primary key (id)
);

create unique index uk_node_ref_file_order on node_ref_file (node_definition_id, ordinal);

-- 판매자가 노드 하나를 엔진에 실제로 돌려 보는 테스트 실행(엔진 계약 v2.0 mode=test)이다.
-- 결과물을 확인하고, 엔진이 잰 달러 원가로 노드 원가를 정한다 (NOD-007). 사용자 돈이 움직이지 않아 장부와 이어지지 않는다.
-- cost_usd 는 엔진이 잰 그대로다. 노드 1회가 0.001 달러대라 소수 여섯째 자리까지 둔다 (계약 8.1).
-- result_json 은 성공한 테스트를 닫을 때 한 번 받아 JSON 으로 읽어 본 결과물(엔진의 봉투)이다. 다시 볼 때 엔진을 부르지 않는다.
-- 하루 횟수를 셀 때 (node_id, requested_at) 로 찾고, 원가를 정할 때 같은 노드의 최근 성공 기록을 찾는다.
create table node_test_run
(
    id           varchar(36)    not null,
    node_id      varchar(36)    not null,
    requester_id bigint         not null,
    run_id       varchar(64)    null,
    mock         boolean        not null,
    status       varchar(20)    not null,
    cost_usd     decimal(20, 6) null,
    failure_code varchar(64)    null,
    result_json  text           null,
    requested_at datetime(6)    not null,
    finished_at  datetime(6)    null,
    primary key (id)
);

create index idx_node_test_run_node_requested on node_test_run (node_id, requested_at);

-- ============================================================
-- V5__create_workflow.sql
-- ============================================================
create table graph_node
(
    id               bigint      not null auto_increment,
    graph_id         varchar(36) not null,
    node_key         varchar(64) not null,
    source_type      varchar(10) not null,
    reference_id     varchar(36) null,
    version_id       varchar(36) null,
    input_source     varchar(20) not null,
    control_type     varchar(20) null,
    branch_condition text        null,
    io_type          varchar(20) null,
    io_fields        text        null,
    result_shape     varchar(20) null,
    result_format    varchar(20) null,
    primary key (id)
);

create unique index uk_graph_node_key on graph_node (graph_id, node_key);
create index idx_graph_node_reference on graph_node (reference_id);

create table graph_edge
(
    id              bigint      not null auto_increment,
    graph_id        varchar(36) not null,
    edge_key        varchar(64) not null,
    source_node_key varchar(64) not null,
    target_node_key varchar(64) not null,
    branch_when     boolean     null,
    primary key (id)
);

create unique index uk_graph_edge_key on graph_edge (graph_id, edge_key);

create table workflow
(
    id                varchar(36)  not null,
    owner_id          bigint       not null,
    graph_id          varchar(36)  not null,
    name              varchar(40)  not null,
    description       varchar(255) null,
    validation_status varchar(20)  not null,
    revision          bigint       not null,
    last_executed_at  datetime(6)  null,
    created_at        datetime(6)  not null,
    updated_at        datetime(6)  not null,
    primary key (id)
);

create index idx_workflow_owner_last_executed on workflow (owner_id, last_executed_at, created_at, id);
create index idx_workflow_owner_created on workflow (owner_id, created_at, id);

-- ============================================================
-- V6__create_agent.sql
-- ============================================================
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

-- ============================================================
-- V7__create_execution.sql
-- ============================================================
-- mock 은 모델을 부르지 않고 도는 실행인지를 접수할 때 적는다. 엔진 계약 v2.0 에서 받을 값이 엔진 원가와 끊겨,
-- 엔진 응답의 mock 칸 하나에만 기대면 그 칸이 빠졌을 때 가짜 결과물에 값을 받게 된다.
-- 이 값으로 mock 실행은 전부 돌려주고 등급 재료로도 세지 않는다.
create table execution
(
    id              varchar(36)    not null,
    workflow_id     varchar(36)    not null,
    requester_id    bigint         not null,
    run_id          varchar(64)    null,
    status          varchar(20)    not null,
    prompt_count    int            not null,
    created_at      datetime(6)    not null,
    updated_at      datetime(6)    not null,
    input_file_key  varchar(128)   null,
    input_file_name varchar(255)   null,
    input_file_size bigint         null,
    request_id      varchar(36)    not null,
    reserved_abc    decimal(41, 6) null,
    mock            boolean        not null default false,
    primary key (id),
    constraint uq_execution_requester_request unique (requester_id, request_id)
);

create index idx_execution_requester_created on execution (requester_id, created_at, id);
create index idx_execution_workflow on execution (workflow_id);

create table execution_result
(
    id           bigint       not null auto_increment,
    execution_id varchar(36)  not null,
    format       varchar(20)  not null,
    file_key     varchar(128) not null,
    file_size    bigint       not null,
    primary key (id)
);

create unique index uk_execution_result_format on execution_result (execution_id, format);

-- 엔진에 보낸 단계마다 실측 원가에 곱할 배율(1 + 등급 할증)과 받을 값(원가 × 배율)이다. 접수할 때 한 번 적고 고치지 않는다.
-- 엔진 계약 v2.0 에서 엔진이 ABC 금액을 주지 않게 되어, 끝난 뒤 받을 값을 접수할 때 정한 단가로 센다.
create table execution_charge_rate
(
    id               bigint auto_increment primary key,
    execution_id     varchar(36)    not null,
    step_id          varchar(16)    not null,
    price_multiplier decimal(6, 4)  not null,
    unit_price       decimal(41, 6) null,
    constraint uq_execution_charge_rate_step unique (execution_id, step_id)
);

-- 실패한 실행이 누구 탓인지다. 실패한 실행마다 한 줄이고, 끝날 때 한 번 적고 고치지 않는다.
-- 실행 ID 가 PK 라 같은 실행의 판정을 두 번 적을 수 없다. 성공한 실행은 줄이 없다.
-- 공급자 · 에이전트는 공급자 탓일 때만 적는다. 성공률 집계(PAY-018)와 정산(PAY-012)이 읽는다.
create table execution_failure
(
    execution_id         varchar(36) not null primary key,
    failed_step_id       varchar(16) null,
    failure_code         varchar(64) null,
    fault                varchar(20) not null,
    affects_success_rate boolean     not null,
    supplier_id          bigint      null,
    agent_id             varchar(36) null
);

-- 끝난 실행에서 성공한 단계마다 그 주인에게 적립한 정산금이다. 실행을 닫을 때 한 번 적고 고치지 않는다.
-- 금액의 합은 같은 실행의 장부 정산 거래(execution-settlement:{실행ID})와 같다. 정산 내역 · 수익 통계(PAY-013/014)가 읽는다.
-- node_name 은 실행할 때 보낸 노드 이름이다. agent_id 는 에이전트 안의 단계일 때만 채운다.
create table execution_settlement
(
    id           bigint auto_increment primary key,
    execution_id varchar(36)    not null,
    step_id      varchar(16)    not null,
    node_name    varchar(40)    null,
    supplier_id  bigint         not null,
    agent_id     varchar(36)    null,
    amount_abc   decimal(41, 6) not null,
    created_at   datetime(6)    not null,
    constraint uq_execution_settlement_step unique (execution_id, step_id)
);

-- 정산 내역 · 수익 통계(PAY-013/014)는 늘 "이 공급자의 것을 기간 안에서 최신순으로" 읽는다.
create index idx_execution_settlement_supplier_created
    on execution_settlement (supplier_id, created_at);

SET FOREIGN_KEY_CHECKS = 1;
