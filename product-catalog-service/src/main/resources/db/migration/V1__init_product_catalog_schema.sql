-- Product Catalog Service — schema quan ly san pham, danh muc, thuong hieu, SKU.
-- Ton kho thuc te (reserve/commit) nam o Order & Inventory Service; stock_quantity
-- o day chi la tham chieu hien thi tren catalog, khong dung de quyet dinh dat hang.

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(150)  NOT NULL,
    description TEXT,
    parent_id   BIGINT,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    -- THIEU TRUOC DAY: parent_id tham chieu toi CHINH bang categories (cay danh muc cha/con)
    -- nhung khong co FK constraint nao bao ve - cho phep ghi 1 parent_id tro toi id KHONG TON TAI
    -- (orphan reference), hoac con cha bi xoa ma khong co canh bao trong khi van con danh muc con
    -- tro toi no. Category.java chi map parent_id nhu 1 "Long" thuong (khong dung @ManyToOne) nen
    -- Hibernate validate KHONG phat hien thieu FK nay luc khoi dong - phai tu doi chieu bang tay.
    -- ON DELETE SET NULL: neu danh muc cha bi xoa, cac danh muc con tro ve "khong co cha" (root)
    -- thay vi chan xoa cung (RESTRICT) hoac xoa day chuyen nguy hiem (CASCADE).
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);

CREATE TABLE brands (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(150)  NOT NULL,
    logo_url    VARCHAR(512),
    description TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_brands_slug UNIQUE (slug)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)  NOT NULL,
    slug        VARCHAR(255)  NOT NULL,
    description TEXT,
    base_price  NUMERIC(15, 2) NOT NULL,
    category_id BIGINT,
    brand_id    BIGINT,
    images      TEXT,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

-- Ten index khop CHINH XAC voi @Index khai bao tren entity Product.java
-- (idx_product_status / idx_product_category / idx_product_brand - so KHONG co
-- "s" sau "product" va dung "category"/"brand" chu khong phai "category_id"/"brand_id").
CREATE INDEX idx_product_category ON products (category_id);
CREATE INDEX idx_product_brand ON products (brand_id);
CREATE INDEX idx_product_status ON products (status);

CREATE TABLE product_variants (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT         NOT NULL,
    sku             VARCHAR(100)   NOT NULL,
    price           NUMERIC(15, 2) NOT NULL,
    stock_quantity  INTEGER        NOT NULL DEFAULT 0,
    attributes      TEXT,
    image_url       VARCHAR(512),
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
