-- V2: Đảm bảo số điện thoại User là duy nhất ở tầng DB
-- PostgreSQL vẫn cho phép nhiều NULL, phù hợp với phone không bắt buộc.
ALTER TABLE users
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);
