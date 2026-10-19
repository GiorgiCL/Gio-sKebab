# Verified menu and lunch allergens

Regular menu items and lunch items both accept and return `allergenInfo` through their existing admin create/update/read APIs and public read APIs. No new endpoint or security boundary is introduced. Authenticated writes retain the existing session and CSRF requirements.

```json
{
  "allergenInfo": {
    "status": "VERIFIED_WITH_ALLERGENS",
    "allergens": ["EGGS", "MILK"]
  }
}
```

`NOT_VERIFIED` and `VERIFIED_NONE_IDENTIFIED` require an empty selection. `VERIFIED_WITH_ALLERGENS` requires at least one of the 14 `Allergen` enum values. Unknown identifiers, null/incomplete objects, null entries and contradictory state/selection combinations return HTTP 400. Duplicate categories collapse into a set. Omitted/null `allergenInfo` defaults to unverified on creation and preserves existing verification on update for compatibility with older clients. To clear existing verification explicitly, submit `NOT_VERIFIED` with `[]`.

Flyway V14 adds a non-null `allergen_status` to each item table and separate `menu_item_allergen` / `lunch_menu_item_allergen` collection tables. Existing rows default to `NOT_VERIFIED`; nothing is inferred or seeded. Foreign keys cascade on deletion, primary keys prevent duplicates, and database checks restrict statuses and category IDs. Cross-field consistency is enforced by the shared immutable `AllergenInfo` contract. JPA enum collections and the status are saved in the existing item transaction; collection-only edits also update the item's modification timestamp. Existing migrations are unchanged.

The stable IDs follow the 14 categories in [Regulation (EU) No 1169/2011, Annex II](https://eur-lex.europa.eu/eli/reg/2011/1169/oj). Account for its specified exemptions when verifying recipes and supplier data. Sulphur dioxide/sulphites use the Annex II threshold of more than 10 mg/kg or 10 mg/l as total SO₂ in products ready for consumption or reconstituted according to the manufacturer's instructions. Category membership does not model individual cereals/nuts, precautionary cross-contact statements, or certify a dish as allergen-free.

Focused tests cover both APIs, every state/category, persistence, collection replacement, ordinary edits from legacy clients, validation, authentication/CSRF and deletion. Migration tests exercise V13→V14 against H2 and PostgreSQL, including preservation of existing rows, defaults, enum/foreign-key/duplicate constraints and cascades. The ordinary suite and `mvnw verify` include the PostgreSQL Testcontainers checks.

Deploy the backend migration/API before the new frontend. Review and explicitly verify each dish in the admin editor before expecting public allergen information. No production content has been populated by this implementation.
