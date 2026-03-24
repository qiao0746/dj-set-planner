$ErrorActionPreference = 'Stop'

$argLine = [string]::Join(' ', ($args | ForEach-Object {
  if ($_ -match '\s') { '"' + $_.Replace('"', '""') + '"' } else { $_ }
}))

mvn -q -DskipTests compile exec:java "-Dexec.args=$argLine"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

