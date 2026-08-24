# Regenerates every PNG asset in resourcepack/assets/starlight/
# Run whenever the pixel art needs to change. No config; edit this script directly.

Add-Type -AssemblyName System.Drawing

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\resourcepack\assets\starlight\textures"))

function New-Bitmap([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    return $bmp
}

function Save-Png($bmp, $path) {
    $dir = Split-Path -Parent $path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $path"
}

function Set-Px($bmp, $x, $y, $r, $g, $b, $a = 255) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $bmp.Width -or $y -ge $bmp.Height) { return }
    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $b))
}

function Fill-Rect($bmp, $x, $y, $w, $h, $r, $g, $b, $a = 255) {
    for ($i = 0; $i -lt $w; $i++) {
        for ($j = 0; $j -lt $h; $j++) {
            Set-Px $bmp ($x + $i) ($y + $j) $r $g $b $a
        }
    }
}

# ---------- MONEY ICON (yellow $ on green shield) ----------
$money = New-Bitmap 16 16
$shape = @(
    "  yyyyyyyyyyyy  ",
    " yyGGGGGGGGGGyy ",
    "yyGGGGGGGGGGGGyy",
    "yGGGgggggggggGGy",
    "yGGgggYYYYgggGGy",
    "yGGggYYyyyYYggGy",
    "yGGggYyyyyyyggGy",
    "yGGgggYYYYyggGGy",
    "yGGgggyyyyYyggGy",
    "yGGggYYYYYYYggGy",
    "yGGgggggggggGGGy",
    "yGGGgggggggGGGGy",
    "yyGGGGGGGGGGGGyy",
    " yyGGGGGGGGGGyy ",
    "  yyyyyyyyyyyy  ",
    "                "
)
for ($y = 0; $y -lt 16; $y++) {
    $row = $shape[$y]
    for ($x = 0; $x -lt 16; $x++) {
        switch ($row[$x]) {
            'y' { Set-Px $money $x $y 255 215 0 }         # bright yellow border
            'G' { Set-Px $money $x $y 46 139 87 }         # green disc
            'g' { Set-Px $money $x $y 34 100 65 }         # darker green
            'Y' { Set-Px $money $x $y 255 255 100 }       # yellow $ highlight
        }
    }
}
Save-Png $money (Join-Path $root "font\money_icon.png")

# ---------- GEMS ICON (cyan faceted diamond) ----------
$gems = New-Bitmap 16 16
$shape = @(
    "                ",
    "      cccc      ",
    "     cCCCCc     ",
    "    cCwwwwCc    ",
    "   cCwWwwWwCc   ",
    "  cCwWWwwWWwCc  ",
    " cCwWWWwwWWWwCc ",
    "cCwWWWWwwWWWWwCc",
    " CwwWWWWWWWWwwC ",
    "  CwwWWWWWWwwC  ",
    "   CwwWWWWwwC   ",
    "    CwwWWwwC    ",
    "     CwwwwC     ",
    "      CwwC      ",
    "       cc       ",
    "                "
)
for ($y = 0; $y -lt 16; $y++) {
    $row = $shape[$y]
    for ($x = 0; $x -lt 16; $x++) {
        switch ($row[$x]) {
            'c' { Set-Px $gems $x $y 0 200 220 }
            'C' { Set-Px $gems $x $y 85 255 255 }
            'W' { Set-Px $gems $x $y 200 255 255 }
            'w' { Set-Px $gems $x $y 140 240 250 }
        }
    }
}
Save-Png $gems (Join-Path $root "font\gems_icon.png")

# ---------- STARS ICON (purple 5-point star with glow) ----------
$stars = New-Bitmap 16 16
$shape = @(
    "                ",
    "       pp       ",
    "      pPPp      ",
    "      pPPp      ",
    "  pppppPPppppp  ",
    " pPPPPPWWPPPPPp ",
    "  pPPPWWWWPPPp  ",
    "   pPPWWWWPPp   ",
    "   pPWWWWWWPp   ",
    "  pPPWWppWWPPp  ",
    "  pPWWp  pWWPp  ",
    " pPPWp    pWPPp ",
    " pPWp      pWPp ",
    " pp          pp ",
    "                ",
    "                "
)
for ($y = 0; $y -lt 16; $y++) {
    $row = $shape[$y]
    for ($x = 0; $x -lt 16; $x++) {
        switch ($row[$x]) {
            'p' { Set-Px $stars $x $y 130 40 180 }        # dark purple outline
            'P' { Set-Px $stars $x $y 190 100 240 }       # purple body
            'W' { Set-Px $stars $x $y 240 210 255 }       # pale core glow
        }
    }
}
Save-Png $stars (Join-Path $root "font\stars_icon.png")

# ---------- EMOJI PLACEHOLDER (colored dot) ----------
$emoji = New-Bitmap 8 8
Fill-Rect $emoji 0 0 8 8 0 0 0 0
Fill-Rect $emoji 1 1 6 6 255 200 100
Fill-Rect $emoji 2 2 4 4 255 240 180
Save-Png $emoji (Join-Path $root "font\emoji_placeholder.png")

# ---------- GUI BACKGROUND (starfield 256x256) ----------
$gui = New-Bitmap 256 256
Fill-Rect $gui 0 0 256 256 10 10 26
# vignette gradient
for ($y = 0; $y -lt 256; $y++) {
    for ($x = 0; $x -lt 256; $x++) {
        $dx = ($x - 128) / 128.0
        $dy = ($y - 128) / 128.0
        $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ($d -gt 1) { $d = 1 }
        $t = 1 - ($d * 0.7)
        $r = [int](10 + 25 * $t)
        $g = [int](10 + 15 * $t)
        $b = [int](26 + 60 * $t)
        Set-Px $gui $x $y $r $g $b
    }
}
# scatter stars deterministically (no RNG so re-runs are stable)
$positions = @(
    @(15,20), @(37,8), @(68,44), @(90,17), @(112,60), @(140,25), @(170,10), @(200,55), @(230,30),
    @(20,80), @(55,110), @(85,95), @(120,120), @(155,80), @(180,105), @(215,90), @(240,120),
    @(30,150), @(60,170), @(95,155), @(125,180), @(160,165), @(190,150), @(220,175), @(245,155),
    @(10,200), @(45,220), @(80,210), @(115,235), @(150,220), @(185,205), @(215,230), @(240,210),
    @(5,50), @(48,140), @(105,190), @(175,60), @(240,80), @(200,200), @(75,240), @(135,10)
)
foreach ($p in $positions) {
    $x = $p[0]; $y = $p[1]
    Set-Px $gui $x $y 255 255 255
    Set-Px $gui ($x + 1) $y 200 200 240
    Set-Px $gui $x ($y + 1) 200 200 240
    Set-Px $gui ($x - 1) $y 130 130 180
    Set-Px $gui $x ($y - 1) 130 130 180
}
Save-Png $gui (Join-Path $root "gui\menu_background.png")

# ---------- CRATE TEXTURES (16x16 top + generic) ----------
function Make-Crate($path, $topR, $topG, $topB) {
    $bmp = New-Bitmap 16 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $edge = ($x -eq 0 -or $x -eq 15 -or $y -eq 0 -or $y -eq 15)
            $inner = ($x -ge 2 -and $x -le 13 -and $y -ge 2 -and $y -le 13)
            if ($edge) {
                Set-Px $bmp $x $y 30 30 40
            } elseif ($inner) {
                Set-Px $bmp $x $y $topR $topG $topB
            } else {
                Set-Px $bmp $x $y ($topR / 2) ($topG / 2) ($topB / 2)
            }
        }
    }
    # accent star in the middle
    Set-Px $bmp 7 5 255 255 255
    Set-Px $bmp 8 5 255 255 255
    Set-Px $bmp 5 7 255 255 255; Set-Px $bmp 6 7 255 255 255
    Set-Px $bmp 9 7 255 255 255; Set-Px $bmp 10 7 255 255 255
    Set-Px $bmp 7 7 255 255 255; Set-Px $bmp 8 7 255 255 255
    Set-Px $bmp 7 8 255 255 255; Set-Px $bmp 8 8 255 255 255
    Set-Px $bmp 6 9 255 255 255; Set-Px $bmp 9 9 255 255 255
    Set-Png-Wrap $bmp $path
}
function Set-Png-Wrap($bmp, $path) { Save-Png $bmp $path }

Make-Crate (Join-Path $root "item\crate_star.png")     85 255 85
Make-Crate (Join-Path $root "item\crate_cosmic.png")   85 255 255
Make-Crate (Join-Path $root "item\crate_galaxy.png")   170 0 170
Make-Crate (Join-Path $root "item\crate_seasonal.png") 255 200 50

Write-Host "All assets regenerated."
