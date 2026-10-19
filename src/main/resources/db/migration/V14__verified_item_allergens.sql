-- Existing dishes deliberately remain unverified. No ingredient inference or allergen seeding.
ALTER TABLE menu_item ADD COLUMN allergen_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VERIFIED'
    CHECK (allergen_status IN ('NOT_VERIFIED', 'VERIFIED_WITH_ALLERGENS', 'VERIFIED_NONE_IDENTIFIED'));

CREATE TABLE menu_item_allergen (
    item_id BIGINT NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    allergen VARCHAR(40) NOT NULL CHECK (allergen IN ('CEREALS_CONTAINING_GLUTEN', 'CRUSTACEANS', 'EGGS', 'FISH', 'PEANUTS', 'SOYBEANS', 'MILK', 'NUTS', 'CELERY', 'MUSTARD', 'SESAME', 'SULPHUR_DIOXIDE_SULPHITES', 'LUPIN', 'MOLLUSCS')),
    PRIMARY KEY (item_id, allergen)
);

ALTER TABLE lunch_menu_item ADD COLUMN allergen_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VERIFIED'
    CHECK (allergen_status IN ('NOT_VERIFIED', 'VERIFIED_WITH_ALLERGENS', 'VERIFIED_NONE_IDENTIFIED'));

CREATE TABLE lunch_menu_item_allergen (
    item_id BIGINT NOT NULL REFERENCES lunch_menu_item(id) ON DELETE CASCADE,
    allergen VARCHAR(40) NOT NULL CHECK (allergen IN ('CEREALS_CONTAINING_GLUTEN', 'CRUSTACEANS', 'EGGS', 'FISH', 'PEANUTS', 'SOYBEANS', 'MILK', 'NUTS', 'CELERY', 'MUSTARD', 'SESAME', 'SULPHUR_DIOXIDE_SULPHITES', 'LUPIN', 'MOLLUSCS')),
    PRIMARY KEY (item_id, allergen)
);

