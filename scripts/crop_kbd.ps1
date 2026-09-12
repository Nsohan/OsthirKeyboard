Add-Type -AssemblyName System.Drawing
$src = "C:\Users\Sohan\.gemini\antigravity-ide\brain\614522c7-a507-49ee-99ed-81bb268dc554\.user_uploaded\media_1789209648477.png"
$img = [System.Drawing.Image]::FromFile($src)
# The keyboard is in the bottom ~40% of the image.
# Let's crop row 3 specifically:
# Image height is say 2400. Keyboard is roughly y=1400 to 2200.
# Row 3 is right above spacebar row.
$w = $img.Width
$h = $img.Height
Write-Host "Image size: $w x $h"

# Crop keyboard area
$kbdH = [int]($h * 0.40)
$kbdY = [int]($h * 0.60)
$rectKbd = New-Object System.Drawing.Rectangle 0, $kbdY, $w, $kbdH
$bmpKbd = New-Object System.Drawing.Bitmap $w, $kbdH
$g = [System.Drawing.Graphics]::FromImage($bmpKbd)
$g.DrawImage($img, 0, 0, $rectKbd, [System.Drawing.GraphicsUnit]::Pixel)
$bmpKbd.Save("C:\Users\Sohan\.gemini\antigravity-ide\brain\614522c7-a507-49ee-99ed-81bb268dc554\kbd_full.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmpKbd.Dispose()

# Crop specifically row 3 (keys m, and the keys to its right)
$r3Y = [int]($h * 0.78)
$r3H = [int]($h * 0.10)
$r3X = [int]($w * 0.55)
$r3W = [int]($w * 0.45)
$rectR3 = New-Object System.Drawing.Rectangle $r3X, $r3Y, $r3W, $r3H
$bmpR3 = New-Object System.Drawing.Bitmap $r3W, $r3H
$g2 = [System.Drawing.Graphics]::FromImage($bmpR3)
$g2.DrawImage($img, 0, 0, $rectR3, [System.Drawing.GraphicsUnit]::Pixel)
$bmpR3.Save("C:\Users\Sohan\.gemini\antigravity-ide\brain\614522c7-a507-49ee-99ed-81bb268dc554\row3_right.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g2.Dispose()
$bmpR3.Dispose()

$img.Dispose()
Write-Host "Done cropping!"
