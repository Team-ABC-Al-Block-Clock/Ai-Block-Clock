ALTER TABLE app_user
    ADD COLUMN nickname VARCHAR(20) NULL AFTER wallet_address;

ALTER TABLE app_user
    ADD COLUMN introduction VARCHAR(40) NULL AFTER nickname;

ALTER TABLE app_user
    ADD COLUMN terms_agreed_at DATETIME(6) NULL AFTER introduction;

ALTER TABLE app_user
    ADD COLUMN recovery_notice_confirmed_at DATETIME(6) NULL AFTER terms_agreed_at;

ALTER TABLE app_user
    ADD COLUMN onboarding_completed_at DATETIME(6) NULL AFTER recovery_notice_confirmed_at;
