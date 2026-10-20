# Prepared business-data import — 2026-10-20

Preparation and rehearsal passed on PostgreSQL 17.9 using a separate loopback-only cluster. Neither `gios_kebab_dev` nor Neon was connected to or changed. The V14 schema and successful V1–V14 Flyway history were reconstructed from the full local backup; no admin-account data was restored from it.

The business archive is readable and contains exactly 14 business tables and four identity-sequence sets. All business rows match an independent restore from the full backup, including Unicode text, prices, hours, image references, translations, and allergen metadata. Foreign keys and check constraints remained enabled throughout restoration.

| Validation | Result |
| --- | --- |
| Categories / menu items / lunch items | 9 / 51 / 2 |
| Restaurant profiles / weekly hours | 1 / 7 |
| Category / menu / profile translations | 27 / 153 / 3; en, ru, ka |
| Lunch translations | 0; existing fallback behavior retained |
| Promotions / promotion translations / special hours | 0 / 0 / 0 |
| Menu / lunch allergen links | 0 / 0 |
| Menu allergen statuses | 49 NOT_VERIFIED; 2 VERIFIED_NONE_IDENTIFIED |
| Lunch allergen statuses | 2 NOT_VERIFIED |
| Menu price range / sum | EUR 1.20–15.00 / EUR 326.70 |
| Lunch price range / sum | EUR 2.50–4.80 / EUR 7.30 |
| Next category / menu / lunch / promotion IDs | 11 / 52 / 9 / 1 |

The raw archive restores the exact `last_value` and `is_called` states. The prepared transfer uses transactional `ALTER SEQUENCE … RESTART` instead of nontransactional `setval()`, preserving the next generated ID. Consequently, `is_called` becomes false after import or rollback; the next-ID behavior is preserved.

Rehearsal covered replacement of existing rows, rollback to different previous prices and sequence values, preservation of a populated disabled admin account and Flyway history, rejection of rollback to another database, and a deliberate failure after sequence resets. That failure rolled back all business changes and sequence resets. Windows line endings were normalized to prevent invalid COPY records. No constraints are disabled and no `CASCADE` is used.

## Windows PostgreSQL TLS setup

The installed PostgreSQL 17.9 client uses OpenSSL 3.0.20. Its default CA file (`C:\Program Files\Common Files\SSL\cert.pem`) and CA directory (`C:\Program Files\Common Files\SSL\certs`) are absent. `PGSSLROOTCERT=system` selects OpenSSL's default trust sources, not the Windows certificate store for this build. Java uses a separate trust configuration, so its successful connection does not establish libpq trust.

Dot-source `Configure-PostgresTls.ps1` in the PowerShell session used for the transfer. It exports the Windows ROOT certificates trusted for TLS server authentication into `windows-root-ca.pem` beside the helper and sets `PGSSLROOTCERT` to that explicit file, with `PGSSLMODE=verify-full`. It needs only the existing Python and PostgreSQL installations. It does not download certificates, change Windows trust, or handle credentials. Re-run it to refresh the snapshot after Windows trust updates. Keep the kit in its existing private directory.

Local validation on 2026-10-20 confirmed that PostgreSQL's own OpenSSL library loaded zero trust objects from its defaults and 49 from the exported bundle. No Neon hostname, credentials, or password file were available to the diagnostic process; authenticated Neon connectivity remains to be checked using the read-only command below. No production import, rollback, data changes, or Render operations were performed during this TLS fix.

References: [PostgreSQL sslrootcert](https://www.postgresql.org/docs/17/libpq-connect.html#LIBPQ-CONNECT-SSLROOTCERT), [certificate and hostname verification](https://www.postgresql.org/docs/17/libpq-ssl.html), [OpenSSL 3.0 default trust paths](https://docs.openssl.org/3.0/man3/SSL_CTX_load_verify_locations/).

## PostgreSQL 18 client correction

Neon runs PostgreSQL 18.6. PostgreSQL 17 `pg_dump` refused the recovery backup before any business-data replacement. The private kit now includes portable Windows x64 PostgreSQL 18.6 binaries from [EDB's official binary archives](https://www.enterprisedb.com/download-postgresql-binaries); the download URL and local SHA-256 are recorded in `postgresql-18.6/DOWNLOAD.json`. No installer, service, cluster, or PATH changes are required. The only transfer-code change selects the bundled client directory. Existing TLS settings, archive pinning, transaction guards, validation, and rollback logic are retained.

Verification on 2026-10-20: `pg_dump`, `pg_restore`, and `psql` each report 18.6. PostgreSQL 18 `pg_restore` read the pinned source archive and prepared all 14 table payloads and four transactional sequence resets offline. A PostgreSQL SSL-request handshake to the saved Neon endpoint succeeded with TLS 1.3, certificate-chain verification, and hostname verification using the unchanged CA bundle. No authentication or SQL was sent: database connection variables and credentials were unavailable to this process, so the authenticated read-only SQL check below remains to be run in the configured operator session. No import, rollback, production-data changes, or Render operations were performed.

Keep the failed recovery directory for inspection. Its zero-byte `before.dump` is not a rollback backup. Retry with a **new** recovery directory; the script deliberately refuses to reuse an existing directory.

In the already configured PowerShell session, after the read-only check below succeeds and while all database writers remain stopped, the retry command is:

```powershell
python "$kit\transfer.py" import --recovery-dir "$kit\recovery-$(Get-Date -Format yyyyMMdd-HHmmss)"
```

Save the reported recovery-directory path as `$recovery` for any subsequent rollback. This correction does not execute the import or rollback.

## Production import

The prepared private kit is `C:\Users\giorg\gios-kebab-backups\business-transfer-prepared-2026-10-20`. Keep it with the two existing dumps. It contains `transfer.py`, `expected.json`, `Configure-PostgresTls.ps1`, and this runbook. It requires the installed Python and the portable PostgreSQL 18.6 client tools included under `postgresql-18.6\pgsql\bin` in the kit; no Python packages are needed. `transfer.py` uses this directory for `pg_dump`, `pg_restore`, and `psql` for both import and rollback. The existing PostgreSQL 17 server and system PATH are unchanged. The archive path and SHA-256 are pinned in `expected.json`.

1. Stop the backend and all other database writers; keep them stopped through verification or rollback. This makes the recovery backup consistent with the pre-import validation snapshot. This is a full replacement of all 14 business tables, including empty source tables.
2. In PowerShell, set the three placeholders from the Neon **direct, unpooled** connection details. The first connection below is read-only: require `ssl = t` and `transaction_read_only = on`. Run the import lines only when ready to replace the business data:

```powershell
$env:PGHOST = '<Neon direct hostname>'
$env:PGDATABASE = '<production database name>'
$env:PGUSER = '<production database role>'
$env:PGPORT = '5432'
$kit = 'C:\Users\giorg\gios-kebab-backups\business-transfer-prepared-2026-10-20'
. "$kit\Configure-PostgresTls.ps1"
$env:PGCONNECT_TIMEOUT = '15'
& "$kit\postgresql-18.6\pgsql\bin\psql.exe" -X -v ON_ERROR_STOP=1 -c "BEGIN READ ONLY; SELECT ssl, version, cipher FROM pg_stat_ssl WHERE pid = pg_backend_pid(); SHOW transaction_read_only; COMMIT;"
if ($LASTEXITCODE -ne 0) { throw 'Read-only connection test failed; stop here.' }

```

After the read-only test succeeds, run these separately when ready to import:

```powershell
$recovery = 'C:\Users\giorg\gios-kebab-backups\neon-before-business-import-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
python "$kit\transfer.py" import --recovery-dir "$recovery"
if ($LASTEXITCODE -ne 0) { throw 'Import stopped; keep the backend stopped and inspect the reported error.' }
```

The script prompts once for a masked database password unless `PGPASSWORD` or `PGPASSFILE` is already set. It never prints or saves credentials. Use the intended production connection environment; remove any stale password variables before starting if necessary.

The script checks the successful V14 Flyway history and checksums, verifies the source archive, takes a business-only recovery dump, and confirms that recovery archive is readable and has all expected entries. It then replaces the business tables and checks every table's count and full-row fingerprint, each next ID, and unchanged admin/Flyway rows **before committing one transaction**. A schema mismatch, backup error, or validation failure stops the operation. A failed transaction retains previous rows and sequence state; a connection loss during commit requires inspection or the rollback below to resolve the outcome.

3. Require the `Import committed` success message. Start the backend and check the public menu, restaurant hours, lunch fallback text, external image rendering, and owner login. Keep the recovery directory. Database restoration preserves image references; it does not transfer image bytes or change external media access.

## Rollback

Keep or return the backend to maintenance mode. In the same PowerShell session, using the same connection variables and `$recovery`, run:

```powershell
python "$kit\transfer.py" rollback --recovery-dir "$recovery"
if ($LASTEXITCODE -ne 0) { throw 'Rollback stopped; keep the backend stopped and inspect the reported error.' }
```

In a new session, restore the connection variables and set `$kit` and `$recovery` to the saved paths first. Rollback restores and validates the previous business rows and next generated IDs in one transaction. It preserves the current admin account and Flyway history; it does not roll back schema migrations. Require `Rollback committed` before restarting the backend. Any business edits made after import would be replaced by this rollback.

No production commands were executed during preparation. Nothing was committed, pushed, or deployed.
