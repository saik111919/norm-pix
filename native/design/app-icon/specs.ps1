Add-Type -AssemblyName System.Drawing
if (-not ('IconKit' -as [type])) {
    Add-Type -ReferencedAssemblies System.Drawing -Path (Join-Path $PSScriptRoot 'IconKit.cs')
}
$dir = $PSScriptRoot
$grid = Join-Path $dir 'emerald-styles.png'
$default = Join-Path $dir 'emerald-default.png'

# Both pictures carry the generator's sparkle at the same spot; the default picture shows it on a plain background.
[IconKit]::Unmark($default, @($grid, $default), 903, 903, 40, 30)

function New-Spec($name, $src, $x0, $y0, $x1, $y1, $corner, $box, $margin, $feather) {
    $s = New-Object Spec
    $s.Name = $name; $s.Source = $src
    $s.X0 = $x0; $s.Y0 = $y0; $s.X1 = $x1; $s.Y1 = $y1; $s.Corner = $corner
    $s.SymbolBox = New-Object System.Drawing.RectangleF $box[0], $box[1], ($box[2] - $box[0]), ($box[3] - $box[1])
    $s.Margin = $margin; $s.Feather = $feather
    return $s
}

# A grid tile inset by the width of its frame; box = the symbol (x0, y0, x1, y1), measured on the picture.
function Tile($name, $x0, $y0, $x1, $y1, $inset, $corner, $box, $margin = 6, $feather = 10) {
    return New-Spec $name $grid ($x0 + $inset) ($y0 + $inset) ($x1 - $inset) ($y1 - $inset) ([Math]::Max(0, $corner - $inset)) $box $margin $feather
}

$specs = @()
$specs += New-Spec 'default' $default 0 0 1024 1024 0 @(178, 203, 868, 782) 36 40
$specs += Tile 'glossy'  25  25 323 323  4 56 @(56, 74, 294, 274)
$specs += Tile 'comic'  364  26 659 321 12 56 @(383, 74, 636, 276)
$specs += Tile 'minimal' 700 25 998 323 18 56 @(736, 78, 963, 271) 4 10
$specs += Tile 'flat'    25 362 323 661  4 56 @(58, 412, 288, 604)
$specs += Tile 'glass'  362 362 661 661  8 56 @(392, 410, 629, 609) 0 24
$specs += Tile 'clay'   700 362 998 661 10 56 @(730, 410, 969, 609)
$specs += Tile 'pixel'   26 701 323 998 18 40 @(61, 746, 290, 950)
# Paper: the cut-out window is the symbol and the cream sheet around it the background.
$specs += Tile 'paper'  362 700 661 999 24 56 @(402, 739, 623, 961) 2 8
$specs += Tile 'neon'   700 701 998 999 26 56 @(736, 749, 961, 946) 4 8
