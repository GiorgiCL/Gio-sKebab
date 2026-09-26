# Menu categories and items

Flyway V3 creates `menu_category` and `menu_item`. IDs are stable database-generated integers. Item `category_id` has a restricted foreign key; a category with any items cannot be deleted, even if they are inactive. Items can be deleted explicitly. Setting `active=false` hides content without deleting it. There is no audit history or generic soft-delete system.

Prices are EUR in `NUMERIC(10,2)` and exposed as `priceEur`; they must be at least EUR 0.01 and have no more than two decimal places. There is no floating-point price storage or currency conversion. Category and item names must be nonblank. Item descriptions are optional as of Flyway V7; existing descriptions remain unchanged and present descriptions cannot be blank. Display orders are nonnegative; equal orders are broken by stable ID. Both tables record creation and last-change instants. Flyway V5 adds a non-null `featured` flag (default `false`) and a nullable `image_url` reference (maximum 2048 characters). Image references are HTTP/HTTPS URLs only; blank values are normalized to null. This is a URL contract only: there is no upload, storage integration, or media metadata. Dietary metadata remains out of scope.

## HTTP contract

`GET /api/public/menu` is anonymous and read-only. It returns `{ "categories": [...] }`, ordered by category display order then ID. Each category contains items ordered by item display order then ID. Only active categories with at least one active item appear. An active item with `available=false` remains visible so the public site can show it as sold out. An empty menu returns an empty categories list. Public items expose `featured` and nullable `imageUrl` as well as their existing display fields; the frontend can select featured items from this response. Public DTOs omit active flags, ordering controls, and timestamps.

The following owner routes require an authenticated session. POST, PUT, and DELETE require a CSRF token:

| Route | Semantics |
| --- | --- |
| `GET/POST /api/admin/menu/categories` | List all categories or create one (201 with `Location`). |
| `GET/PUT/DELETE /api/admin/menu/categories/{id}` | Read, fully replace, or delete one category. Delete returns 409 while any item refers to it. |
| `GET/POST /api/admin/menu/items` | List all items or create one (201 with `Location`). |
| `GET/PUT/DELETE /api/admin/menu/items/{id}` | Read, fully replace, or delete one item. |

PUT requires every required request field, including active/available and featured flags, and returns 404 for an unknown target. Item `description` may be omitted or null to clear it; whitespace-only input normalizes to null, while nonblank text remains unchanged and may be at most 1000 characters. `imageUrl` may be omitted as null, set to an HTTP/HTTPS URL, replaced, or cleared with null/blank. A missing referenced category returns 404. Deleting an existing resource returns 204; deleting it again returns 404. Invalid JSON, invalid required names, oversized present descriptions, bad prices, invalid image URLs, and negative orders return a generic 400 problem response. Database constraint races return a generic 409. Admin lists include hidden and sold-out content so the owner can manage it. No write endpoint exists under `/api/public/**`.

Flyway V6 adds category and item translation tables for en/ru/ka. Lithuanian remains in the original columns. Public menu GET accepts optional `?lang=lt|en|ru|ka`, resolves names and descriptions field by field, and rejects unsupported values with 400. A missing requested-language description falls back to Lithuanian if present, then null. Public and admin item DTOs expose `description: null` when absent. Admin responses include `translations` keyed by locale, with `{name}` for categories and `{name, description}` for items. A translated name does not require a translated description. Blank translated descriptions normalize to null. A supplied map replaces non-Lithuanian translations; an omitted map preserves them for older clients. Price, flags, order, and image URL remain common to all languages.

Fast H2 HTTP tests cover content filtering, ordering, CRUD, validation, image/featured behavior, localization, and security. `mvnw verify` also runs the disposable PostgreSQL 17.9 integration test for V1 through V7 and database constraints. See [BACKEND_FOUNDATION.md](BACKEND_FOUNDATION.md).
