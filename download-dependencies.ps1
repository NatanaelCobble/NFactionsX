param([string]$Destination="$PSScriptRoot/deps")
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force $Destination | Out-Null
$artifacts=@(
    @('powernukkitx-3.0.6.jar','https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.6/powernukkitx.jar','10822D818C1F2C7D423092A235F3F04BD0EB915B2298B204ED1697D7A0DD9539'),
    @('NVaultX-API-1.0.0.jar','https://github.com/NatanaelCobble/NVaultX/releases/download/v1.0.0/NVaultX-API-1.0.0.jar','AFB9EF618F4999FEF38314290997A3508CDC5D53728DE2099EE5BB242E1D18BF')
)
foreach($artifact in $artifacts){
    $path=Join-Path $Destination $artifact[0]
    if(-not(Test-Path $path)){Invoke-WebRequest -Uri $artifact[1] -OutFile $path}
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $artifact[2]){throw "Empreinte inattendue : $($artifact[0])"}
    Write-Output "Verifie : $($artifact[0])"
}
