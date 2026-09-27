-- Provide the restaurant's approved description in each public locale while
-- retaining the normal Lithuanian fallback for other incomplete content.
UPDATE restaurant_profile_translation
SET description = 'Kebabs and grilled dishes on Savanorių Avenue, with house-made sauces and Georgian flavours.'
WHERE profile_id = 1 AND locale = 'en';
INSERT INTO restaurant_profile_translation (profile_id, locale, description)
SELECT id, 'en', 'Kebabs and grilled dishes on Savanorių Avenue, with house-made sauces and Georgian flavours.'
FROM restaurant_profile p
WHERE id = 1 AND NOT EXISTS (
    SELECT 1 FROM restaurant_profile_translation t WHERE t.profile_id = p.id AND t.locale = 'en'
);

UPDATE restaurant_profile_translation
SET description = 'Кебабы и блюда на гриле на проспекте Саванорю, с соусами собственного приготовления и грузинскими нотками.'
WHERE profile_id = 1 AND locale = 'ru';
INSERT INTO restaurant_profile_translation (profile_id, locale, description)
SELECT id, 'ru', 'Кебабы и блюда на гриле на проспекте Саванорю, с соусами собственного приготовления и грузинскими нотками.'
FROM restaurant_profile p
WHERE id = 1 AND NOT EXISTS (
    SELECT 1 FROM restaurant_profile_translation t WHERE t.profile_id = p.id AND t.locale = 'ru'
);

UPDATE restaurant_profile_translation
SET description = 'ქაბაბები და გრილზე მომზადებული კერძები სავანორიუს გამზირზე, ჩვენი მომზადებული სოუსებითა და ქართული გემოებით.'
WHERE profile_id = 1 AND locale = 'ka';
INSERT INTO restaurant_profile_translation (profile_id, locale, description)
SELECT id, 'ka', 'ქაბაბები და გრილზე მომზადებული კერძები სავანორიუს გამზირზე, ჩვენი მომზადებული სოუსებითა და ქართული გემოებით.'
FROM restaurant_profile p
WHERE id = 1 AND NOT EXISTS (
    SELECT 1 FROM restaurant_profile_translation t WHERE t.profile_id = p.id AND t.locale = 'ka'
);
