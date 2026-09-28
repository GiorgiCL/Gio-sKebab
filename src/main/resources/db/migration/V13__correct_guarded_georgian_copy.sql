-- Correct only known imported Georgian translations. Later owner edits stay intact.
UPDATE restaurant_profile_translation
SET description = 'ქებაბები და გრილზე მომზადებული კერძები სავანორიუს გამზირზე, ჩვენი მომზადებული სოუსებითა და ქართული გემოებით.'
WHERE profile_id = 1 AND locale = 'ka'
  AND description = 'ქაბაბები და გრილზე მომზადებული კერძები სავანორიუს გამზირზე, ჩვენი მომზადებული სოუსებითა და ქართული გემოებით.';

UPDATE menu_category_translation SET name = 'დიდი ქებაბები'
WHERE locale = 'ka' AND name = 'დიდი ქაბაბები'
  AND category_id IN (SELECT id FROM menu_category WHERE name = 'Dideli kebabai');
UPDATE menu_category_translation SET name = 'ვეგეტარიანული ქებაბები'
WHERE locale = 'ka' AND name = 'ვეგეტარიანული ქაბაბები'
  AND category_id IN (SELECT id FROM menu_category WHERE name = 'Vegetariškas kebabas');
UPDATE menu_category_translation SET name = 'ქებაბი თეფშზე'
WHERE locale = 'ka' AND name = 'ქაბაბი თეფშზე'
  AND category_id IN (SELECT id FROM menu_category WHERE name = 'Kebabas lėkštėje');

UPDATE menu_item_translation SET name = 'ქებაბი ქათმის მკერდის ფილით'
WHERE locale = 'ka' AND name = 'ქაბაბი ქათმის მკერდის ფილეთი'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su vištienos krūtinėle');
UPDATE menu_item_translation SET name = 'ქებაბი ქათმის ბარძაყის ხორცით'
WHERE locale = 'ka' AND name = 'ქაბაბი ქათმის ბარძაყის ხორცით'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su vištienos kumpeliais');
UPDATE menu_item_translation SET name = 'ქებაბი ინდაურის ხორცით'
WHERE locale = 'ka' AND name = 'ქაბაბი ინდაურის ხორცით'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su kalakutiena');
UPDATE menu_item_translation SET name = 'ქებაბი ღორის ხორცით'
WHERE locale = 'ka' AND name = 'ქაბაბი ღორის ხორცით'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su kiauliena');
UPDATE menu_item_translation SET name = 'ქებაბი საქონლის ანტრეკოტით'
WHERE locale = 'ka' AND name = 'ქაბაბი საქონლის ანტრეკოტით'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su jautienos antrekotu');
UPDATE menu_item_translation SET name = 'საქონლის ხორცის ლულა-ქებაბი'
WHERE locale = 'ka' AND name = 'საქონლის ხორცის ლულა-ქაბაბი'
  AND item_id IN (SELECT id FROM menu_item WHERE name IN ('Jautienos Liulia Kebabas', 'Jautienos liulia kebabas'));
UPDATE menu_item_translation SET name = 'ვეგეტარიანული ქებაბი'
WHERE locale = 'ka' AND name = 'ვეგეტარიანული ქაბაბი'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Vegetariškas kebabas');
UPDATE menu_item_translation SET name = 'ქებაბი თხის ყველით'
WHERE locale = 'ka' AND name = 'ქაბაბი თხის ყველით'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas su ožkos sūriu');
UPDATE menu_item_translation SET name = 'ქებაბი თეფშზე'
WHERE locale = 'ka' AND name = 'ქაბაბი თეფშზე'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Kebabas lėkštėje');

-- These four imported names are in the sauces and sides category. Keep the bun description untouched.
UPDATE menu_item_translation SET name = 'ნივრის სოუსი'
WHERE locale = 'ka' AND name = 'ნივრის სოუსი (ხელნაკეთი)'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Česnakinis padažas (Rankų darbo)');
UPDATE menu_item_translation SET name = 'ჩედერის ყველი'
WHERE locale = 'ka' AND name = 'ჩედერის ყველი (ხელნაკეთი)'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Čederio sūrio (Rankų darbo)');
UPDATE menu_item_translation SET name = 'მანგო'
WHERE locale = 'ka' AND name = 'მანგო (ხელნაკეთი)'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Mango (Rankų darbo)');
UPDATE menu_item_translation SET name = 'პომიდვრის სოუსი'
WHERE locale = 'ka' AND name = 'პომიდვრის სოუსი (ხელნაკეთი)'
  AND item_id IN (SELECT id FROM menu_item WHERE name = 'Pomidorų padažas (Rankų darbo)');
