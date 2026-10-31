$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/docker-native.ps1"

Invoke-Docker compose exec -T keycloak sh -c '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password $KC_BOOTSTRAP_ADMIN_PASSWORD'
if ($LASTEXITCODE -ne 0) { throw 'Keycloak admin authentication failed.' }
$clients = Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get clients -r dx-lab -q clientId=dx-permissions --fields id
if ($LASTEXITCODE -ne 0) { throw 'Cannot find permission client.' }
$client = @((($clients -join "`n") | ConvertFrom-Json))[0].id
if (-not $client) { throw 'Permission client is missing.' }
$roles = Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get "clients/$client/roles" -r dx-lab
if ($LASTEXITCODE -ne 0) { throw 'Cannot read permission roles.' }
if ('document:download' -notin ((($roles -join "`n") | ConvertFrom-Json).name)) {
    Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create "clients/$client/roles" -r dx-lab -s name=document:download
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create download permission.' }
}
$template = Get-Content -LiteralPath infra/keycloak/realm-template.json -Raw -Encoding UTF8 | ConvertFrom-Json
$workspaces = @($template.groups | Where-Object name -eq workspaces)[0].subGroups
foreach ($workspace in $workspaces) {
    foreach ($role in @('requester', 'agent', 'coordinator')) {
        $group = Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get "group-by-path/workspaces/$($workspace.name)/$role" -r dx-lab
        if ($LASTEXITCODE -ne 0) { throw "Cannot read group $($workspace.name)/$role" }
        $groupId = (($group -join "`n") | ConvertFrom-Json).id
        Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh add-roles -r dx-lab --gid $groupId --cclientid dx-permissions --rolename document:view
        if ($LASTEXITCODE -ne 0) { throw 'Cannot grant document preview permission.' }
        if ($role -ne 'requester') {
            Invoke-Docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh add-roles -r dx-lab --gid $groupId --cclientid dx-permissions --rolename document:download
            if ($LASTEXITCODE -ne 0) { throw 'Cannot grant staff download permission.' }
        }
    }
}
Write-Host 'Knowledge permissions updated. No realms, users, documents or database data were deleted. Sign in again to refresh permissions.'
