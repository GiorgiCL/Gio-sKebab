-- Correct the imported category wording, without replacing later owner edits.
-- Products and all other category properties remain unchanged.
UPDATE menu_category_translation
SET name = CASE locale
    WHEN 'en' THEN 'Large Kebabs'
    WHEN 'ru' THEN 'Большие кебабы'
    WHEN 'ka' THEN 'დიდი ქაბაბები'
END
WHERE category_id IN (SELECT id FROM menu_category WHERE name = 'Susirink savo KEBABĄ (Didelis)')
  AND ((locale = 'en' AND name = 'Build Your Own Kebab (Large)')
    OR (locale = 'ru' AND name = 'Собери свой кебаб (большой)')
    OR (locale = 'ka' AND name = 'ააწყვე შენი ქაბაბი (დიდი)'));

UPDATE menu_category
SET name = 'Dideli kebabai', updated_at = CURRENT_TIMESTAMP
WHERE name = 'Susirink savo KEBABĄ (Didelis)';
