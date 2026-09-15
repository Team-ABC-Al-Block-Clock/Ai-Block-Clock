-- Preserve the existing 35 integer digits while expanding ABC to six decimal places.
ALTER TABLE ledger_account
    MODIFY COLUMN balance DECIMAL(41, 6) NOT NULL DEFAULT 0.000000;

ALTER TABLE ledger_entry
    MODIFY COLUMN amount_delta DECIMAL(41, 6) NOT NULL;

ALTER TABLE charge_request
    MODIFY COLUMN credited_abc_amount DECIMAL(41, 6) NULL;
