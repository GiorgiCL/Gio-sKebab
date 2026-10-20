# Dot-source this file before running the prepared transfer kit.
# Exports only Windows ROOT certificates trusted for TLS server authentication.
param(
    [string]$CertificatePath = (Join-Path $PSScriptRoot 'windows-root-ca.pem')
)

$exportRoots = @'
import os
from pathlib import Path
import ssl
import sys
import tempfile

path = Path(sys.argv[1]).resolve()
server_auth = ssl.Purpose.SERVER_AUTH.oid
roots = sorted({der for der, encoding, trust in ssl.enum_certificates("ROOT")
                if encoding == "x509_asn" and
                (trust is True or server_auth in trust)})
if not roots:
    raise SystemExit("No Windows roots trusted for TLS server authentication")
pem = "".join(ssl.DER_cert_to_PEM_cert(der) for der in roots)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
context.load_verify_locations(cadata=pem)
with tempfile.NamedTemporaryFile(mode="w", encoding="ascii", newline="\n",
                                 dir=path.parent, delete=False) as output:
    temporary = output.name
    output.write(pem)
try:
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
print(f"Exported {len(roots)} Windows TLS trust roots to {path}")
'@

$exportRoots | & python - $CertificatePath
if ($LASTEXITCODE -ne 0) { throw 'TLS root export failed; stop before connecting.' }
$env:PGSSLROOTCERT = (Resolve-Path -LiteralPath $CertificatePath).Path
$env:PGSSLMODE = 'verify-full'
Write-Output 'PostgreSQL TLS certificate and hostname verification enabled (verify-full).'
