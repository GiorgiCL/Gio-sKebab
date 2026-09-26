CREATE TABLE restaurant_profile_translation (
    profile_id SMALLINT NOT NULL REFERENCES restaurant_profile(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale IN ('en', 'ru', 'ka')),
    display_name VARCHAR(160),
    description VARCHAR(1000),
    PRIMARY KEY (profile_id, locale),
    CHECK (display_name IS NULL OR TRIM(display_name) <> ''),
    CHECK (description IS NULL OR TRIM(description) <> ''),
    CHECK (display_name IS NOT NULL OR description IS NOT NULL)
);

CREATE TABLE menu_category_translation (
    category_id BIGINT NOT NULL REFERENCES menu_category(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale IN ('en', 'ru', 'ka')),
    name VARCHAR(160) NOT NULL CHECK (TRIM(name) <> ''),
    PRIMARY KEY (category_id, locale)
);

CREATE TABLE menu_item_translation (
    item_id BIGINT NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale IN ('en', 'ru', 'ka')),
    name VARCHAR(160),
    description VARCHAR(1000),
    PRIMARY KEY (item_id, locale),
    CHECK (name IS NULL OR TRIM(name) <> ''),
    CHECK (description IS NULL OR TRIM(description) <> ''),
    CHECK (name IS NOT NULL OR description IS NOT NULL)
);

CREATE TABLE promotion_translation (
    promotion_id BIGINT NOT NULL REFERENCES promotion(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale IN ('en', 'ru', 'ka')),
    title VARCHAR(160),
    description VARCHAR(500),
    PRIMARY KEY (promotion_id, locale),
    CHECK (title IS NULL OR TRIM(title) <> ''),
    CHECK (description IS NULL OR TRIM(description) <> ''),
    CHECK (title IS NOT NULL OR description IS NOT NULL)
);
