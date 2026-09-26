-- 승인된 출금을 보관 지갑에서 abcToken 으로 보낸 기록이다(PAY-025).
-- 서명한 거래와 그 해시를 보내기 전에 적는다. 보냈는지 모를 때 같은 거래를 그대로 다시 보내려고 원문도 둔다.
-- 서명된 거래는 체인에 퍼지면 누구나 볼 수 있는 값이라 비밀이 아니다.
alter table withdrawal
    add column payout_transaction_hash varchar(66) null after reject_reason;

alter table withdrawal
    add column payout_raw_transaction text null after payout_transaction_hash;

alter table withdrawal
    add column payout_submitted_at datetime(6) null after payout_raw_transaction;

alter table withdrawal
    add column paid_at datetime(6) null after payout_submitted_at;

-- 한 거래가 두 출금에 쓰이지 않는다.
alter table withdrawal
    add constraint uq_withdrawal_payout_transaction_hash unique (payout_transaction_hash);
