# Runs from the Prism pre-launch command after packwiz. Once per instance, copies the player's
# settings from the normal launcher (%APPDATA%\.minecraft) into this instance: options.txt
# (keybinds, sensitivity, FOV, sound, GUI scale, language, ...) and servers.dat (server list).
# Never blocks the launch: any failure just skips the import.
try {
    $mc = if ($env:INST_MC_DIR) { $env:INST_MC_DIR } else { (Get-Location).Path }
    $marker = Join-Path $mc 'aegir\.vanilla-imported'
    if (Test-Path $marker) { exit 0 }
    $vanilla = Join-Path $env:APPDATA '.minecraft'

    $src = Join-Path $vanilla 'options.txt'
    $dst = Join-Path $mc 'options.txt'
    if (Test-Path $src) {
        $opts = [ordered]@{}
        $read = {
            param($path)
            foreach ($line in [IO.File]::ReadAllLines($path)) {
                $i = $line.IndexOf(':')
                if ($i -gt 0) { $opts[$line.Substring(0, $i)] = $line.Substring($i + 1) }
            }
        }
        if (Test-Path $dst) { & $read $dst }
        $keep = @{}
        foreach ($k in 'version', 'resourcePacks', 'incompatibleResourcePacks') {
            if ($opts.Contains($k)) { $keep[$k] = $opts[$k] }
        }
        & $read $src
        # Pack-specific values: the instance's data version (1.21.1) and resource packs stay.
        foreach ($k in 'version', 'resourcePacks', 'incompatibleResourcePacks') {
            if ($keep.Contains($k)) { $opts[$k] = $keep[$k] } else { $opts.Remove($k) }
        }
        if (-not $opts.Contains('version')) { $opts.Insert(0, 'version', '3955') }
        # Vanilla-sized view distances are too heavy for this pack; Distant Horizons draws the far terrain.
        foreach ($cap in @(@('renderDistance', 12), @('simulationDistance', 8))) {
            $v = 0
            if ([int]::TryParse([string]$opts[$cap[0]], [ref]$v) -and $v -gt $cap[1]) { $opts[$cap[0]] = [string]$cap[1] }
        }
        $opts['onboardAccessibility'] = 'false'
        $lines = foreach ($e in $opts.GetEnumerator()) { "$($e.Key):$($e.Value)" }
        [IO.File]::WriteAllLines($dst, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
    }

    $servers = Join-Path $vanilla 'servers.dat'
    if ((Test-Path $servers) -and -not (Test-Path (Join-Path $mc 'servers.dat'))) {
        Copy-Item $servers (Join-Path $mc 'servers.dat')
    }

    New-Item -ItemType File -Force $marker | Out-Null
} catch {
    Write-Host "AEGIR: skipped importing vanilla settings: $_"
}
exit 0
