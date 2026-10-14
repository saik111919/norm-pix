. (Join-Path $PSScriptRoot 'specs.ps1')
# Builds every icon's layers at 432 px and a sheet of them as launchers show them (round, squircle, and small), in
# %TEMP%\normpix-app-icon. Writes nothing into the app.
$out = Join-Path $env:TEMP 'normpix-app-icon'
New-Item -ItemType Directory -Force $out | Out-Null
$cell = 220
$sheet = New-Object System.Drawing.Bitmap ($cell * 5), ($cell * 6)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(255, 120, 124, 130))
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 245, 245, 240))), 0, ($cell * 4), ($cell * 5), ($cell * 2))
$i = 0
foreach ($s in $specs) {
    $r = [IconKit]::Analyze($s)
    $r.ToString()
    $fg = [IconKit]::Foreground($r, 432)
    $bg = [IconKit]::Background($r, 432)
    [IconKit]::Save($fg, (Join-Path $out "$($s.Name)_fg.png"))
    [IconKit]::SaveOpaque($bg, (Join-Path $out "$($s.Name)_bg.png"))
    $col = $i % 5; $row = [Math]::Floor($i / 5)
    $round = [IconKit]::Masked($bg, $fg, 180, $true)
    $squircle = [IconKit]::Masked($bg, $fg, 180, $false)
    $small = [IconKit]::Masked($bg, $fg, 64, $true)
    $g.DrawImage($round, ($col * $cell + 20), ($row * $cell + 20), 180, 180)
    $g.DrawImage($squircle, ($col * $cell + 20), (($row + 2) * $cell + 20), 180, 180)
    $g.DrawImage($small, ($col * $cell + 20), (($row + 4) * $cell + 20), 64, 64)
    $g.DrawImage($small, ($col * $cell + 100), (($row + 4) * $cell + 20), 48, 48)
    $round.Dispose(); $squircle.Dispose(); $small.Dispose(); $fg.Dispose(); $bg.Dispose()
    $i++
}
$g.Dispose()
$sheet.Save((Join-Path $out 'sheet.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()
