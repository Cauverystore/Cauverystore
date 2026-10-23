-- CauveryStore multilingual - phase 1
-- RUN THIS BY HAND (psql / pgAdmin / your host's SQL console) BEFORE deploying the new backend.
-- Your app has spring.flyway.enabled=false, so nothing runs it automatically.
-- It is safe to run twice (everything is IF NOT EXISTS / ON CONFLICT DO NOTHING).
-- It only ADDS new tables. It does not change or delete anything in your existing tables.

-- Optional: speeds up "contains" search on large catalogues. Skipped quietly if your host does not allow it.
DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'pg_trgm not available - search still works, just without the trigram index';
END $$;

-- ---------------------------------------------------------------- languages
CREATE TABLE IF NOT EXISTS languages (
    code            VARCHAR(10)  PRIMARY KEY,
    name_english    VARCHAR(60)  NOT NULL,
    name_native     VARCHAR(60)  NOT NULL,
    script          VARCHAR(20)  NOT NULL,
    text_direction  VARCHAR(3)   NOT NULL DEFAULT 'ltr',
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 100
);

INSERT INTO languages (code, name_english, name_native, script, is_default, sort_order) VALUES
 ('en','English','English','Latn',TRUE,1),
 ('ta','Tamil','தமிழ்','Taml',FALSE,2),
 ('te','Telugu','తెలుగు','Telu',FALSE,3),
 ('hi','Hindi','हिन्दी','Deva',FALSE,4),
 ('kn','Kannada','ಕನ್ನಡ','Knda',FALSE,5),
 ('ml','Malayalam','മലയാളം','Mlym',FALSE,6)
ON CONFLICT (code) DO NOTHING;

-- ------------------------------------------------- product translations
-- One row per product per language. The ORIGINAL text stays in products.name / products.description;
-- the site falls back to it whenever a language has no PUBLISHED translation.
CREATE TABLE IF NOT EXISTS product_translations (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    language_code     VARCHAR(10)  NOT NULL REFERENCES languages(code),
    name              VARCHAR(500) NOT NULL,
    slug              VARCHAR(520),
    short_description TEXT,
    description       TEXT,
    meta_title        VARCHAR(255),
    meta_description  TEXT,
    -- MACHINE_DRAFT, SELLER_EDITED, IN_REVIEW, APPROVED, REJECTED, PUBLISHED  (phase 1 uses DRAFT / PUBLISHED)
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    source            VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    version           INT          NOT NULL DEFAULT 1,
    updated_by        BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_translation UNIQUE (product_id, language_code)
);
CREATE INDEX IF NOT EXISTS ix_pt_lang_status ON product_translations (language_code, status);
CREATE INDEX IF NOT EXISTS ix_pt_product     ON product_translations (product_id);

-- ------------------------------------------------ category translations
CREATE TABLE IF NOT EXISTS category_translations (
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT       NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    language_code VARCHAR(10)  NOT NULL REFERENCES languages(code),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    source        VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    version       INT          NOT NULL DEFAULT 1,
    updated_by    BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_translation UNIQUE (category_id, language_code)
);

-- --------------------------------------------- saved language per user
CREATE TABLE IF NOT EXISTS user_language_preferences (
    user_id       BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    language_code VARCHAR(10) NOT NULL REFERENCES languages(code),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------- search
-- tokens = the product text split into words by the app (NOT PostgreSQL's built-in text search, which splits
-- Tamil/Hindi words badly because of combining vowel marks).
CREATE TABLE IF NOT EXISTS product_search_index (
    product_id  BIGINT    PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    all_text    TEXT      NOT NULL,
    tokens      TEXT[]    NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_psi_tokens ON product_search_index USING gin (tokens);
DO $$
BEGIN
  CREATE INDEX IF NOT EXISTS ix_psi_trgm ON product_search_index USING gin (all_text gin_trgm_ops);
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'trigram index skipped';
END $$;

-- Words that mean the same thing across languages. Search treats every term in a concept as equal.
-- This is a STARTER list - extend it (admin screen comes later; for now INSERT rows).
CREATE TABLE IF NOT EXISTS search_synonyms (
    id            BIGSERIAL PRIMARY KEY,
    concept_key   VARCHAR(80)  NOT NULL,
    language_code VARCHAR(10)  NOT NULL REFERENCES languages(code),
    term          VARCHAR(120) NOT NULL,
    roman_term    VARCHAR(120),
    CONSTRAINT uq_synonym UNIQUE (language_code, term)
);
CREATE INDEX IF NOT EXISTS ix_syn_concept ON search_synonyms (concept_key);

INSERT INTO search_synonyms (concept_key, language_code, term, roman_term) VALUES
 ('cotton','en','cotton','cotton'),
 ('cotton','ta','பருத்தி','paruthi'),
 ('cotton','hi','सूती','sooti'),
 ('cotton','hi','कॉटन','cotton'),
 ('cotton','te','పత్తి','patti'),
 ('cotton','kn','ಹತ್ತಿ','hatti'),
 ('cotton','ml','പരുത്തി','parutthi'),
 ('bedsheet','en','bedsheet','bedsheet'),
 ('bedsheet','ta','விரிப்பு','virippu'),
 ('bedsheet','hi','बेडशीट','bedsheet'),
 ('bedsheet','hi','चादर','chadar'),
 ('bedsheet','te','బెడ్‌షీట్','bedsheet'),
 ('bedsheet','kn','ಬೆಡ್‌ಶೀಟ್','bedsheet'),
 ('bedsheet','ml','ബെഡ്ഷീറ്റ്','bedsheet'),
 ('saree','en','saree','saree'),
 ('saree','ta','சேலை','selai'),
 ('saree','hi','साड़ी','saree'),
 ('saree','te','చీర','cheera'),
 ('saree','kn','ಸೀರೆ','seere'),
 ('saree','ml','സാരി','saree'),
 ('silk','en','silk','silk'),
 ('silk','ta','பட்டு','pattu'),
 ('silk','hi','रेशम','resham'),
 ('silk','te','పట్టు','pattu'),
 ('silk','kn','ರೇಷ್ಮೆ','reshme'),
 ('silk','ml','പട്ട്','pattu'),
 ('rice','en','rice','rice'),
 ('rice','ta','அரிசி','arisi'),
 ('rice','hi','चावल','chawal'),
 ('rice','te','బియ్యం','biyyam'),
 ('rice','kn','ಅಕ್ಕಿ','akki'),
 ('rice','ml','അരി','ari'),
 ('turmeric','en','turmeric','turmeric'),
 ('turmeric','ta','மஞ்சள்','manjal'),
 ('turmeric','hi','हल्दी','haldi'),
 ('turmeric','te','పసుపు','pasupu'),
 ('turmeric','kn','ಅರಿಶಿನ','arishina'),
 ('turmeric','ml','മഞ്ഞൾ','manjal')
ON CONFLICT DO NOTHING;
