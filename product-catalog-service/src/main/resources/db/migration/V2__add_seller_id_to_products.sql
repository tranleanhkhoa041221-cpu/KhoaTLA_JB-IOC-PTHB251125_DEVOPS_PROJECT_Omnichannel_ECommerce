-- ============================================================================
-- V2: Data Ownership / IDOR fix - them cot seller_id cho bang products.
--
-- TRUOC MIGRATION NAY: Product khong co bat ky cot ownership nao -> bat ky user co role
-- SELLER (hoac ADMIN) deu sua/xoa duoc MOI san pham trong he thong, ke ca san pham do MOT
-- SELLER KHAC tao (IDOR: Seller A sua/xoa duoc san pham cua Seller B). Them cot nay de
-- ProductServiceImpl/ProductController co the gan va kiem tra ownership (xem Product.java,
-- ProductServiceImpl.create()/update()/delete()).
--
-- Nullable vi khong co gia tri mac dinh hop ly cho cac san pham DA TON TAI truoc migration
-- (khong the "doan" ai la nguoi tao that su) - cac san pham nay se tam thoi CHI ADMIN moi
-- sua/xoa duoc cho den khi duoc ADMIN gan lai seller_id (hanh vi an toan: thieu thong tin
-- ownership -> mac dinh han che quyen, khong mo rong quyen).
-- ============================================================================

ALTER TABLE products ADD COLUMN seller_id BIGINT;

CREATE INDEX idx_products_seller_id ON products (seller_id);
