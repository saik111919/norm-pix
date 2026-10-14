param([string]$Res = (Join-Path $PSScriptRoot '..\..\app\src\main\res'))
. (Join-Path $PSScriptRoot 'specs.ps1')
# Writes the launcher icons into the app's resources:
# - the default icon at every density (drawable-*dpi/ic_launcher_foreground|background.png);
# - each alternative once (drawable-nodpi/ic_launcher_<name>_foreground|background.png), scaled by the system.
# No pre-adaptive mipmap-*dpi/ic_launcher.png: minSdk is 26, so mipmap-anydpi/ic_launcher.xml is always the one used.
$densities = [ordered]@{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }
foreach ($s in $specs) {
    $r = [IconKit]::Analyze($s)
    if ($s.Name -eq 'default') {
        foreach ($d in $densities.Keys) {
            $layer = [int](108 * $densities[$d])
            $fg = [IconKit]::Foreground($r, $layer)
            $bg = [IconKit]::Background($r, $layer)
            [IconKit]::Save($fg, (Join-Path $Res "drawable-$d\ic_launcher_foreground.png"))
            [IconKit]::SaveOpaque($bg, (Join-Path $Res "drawable-$d\ic_launcher_background.png"))
            $fg.Dispose(); $bg.Dispose()
        }
    } else {
        $fg = [IconKit]::Foreground($r, 324)
        $bg = [IconKit]::Background($r, 216)
        [IconKit]::Save($fg, (Join-Path $Res "drawable-nodpi\ic_launcher_$($s.Name)_foreground.png"))
        [IconKit]::SaveOpaque($bg, (Join-Path $Res "drawable-nodpi\ic_launcher_$($s.Name)_background.png"))
        $fg.Dispose(); $bg.Dispose()
    }
    "wrote $($s.Name)"
}
