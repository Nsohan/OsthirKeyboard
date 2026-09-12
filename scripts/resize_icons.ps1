Add-Type -AssemblyName System.Drawing

function Resize-Image {
    param(
        [string]$src,
        [string]$dest,
        [int]$width,
        [int]$height
    )
    $srcImg = [System.Drawing.Image]::FromFile($src)
    $destImg = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($destImg)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($srcImg, 0, 0, $width, $height)
    $destImg.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $destImg.Dispose()
    $srcImg.Dispose()
    Write-Host "Generated $dest ($width x $height)"
}

$root = "e:\Development\AndroidApp\AMCustomKeyboard-main"
$srcLogo = "$root\Logo.png"

Resize-Image $srcLogo "$root\res\drawable\app_logo.png" 256 256
Resize-Image $srcLogo "$root\res\mipmap-mdpi\ic_launcher.png" 48 48
Resize-Image $srcLogo "$root\res\mipmap-hdpi\ic_launcher.png" 72 72
Resize-Image $srcLogo "$root\res\mipmap-xhdpi\ic_launcher.png" 96 96
Resize-Image $srcLogo "$root\res\mipmap-xxhdpi\ic_launcher.png" 144 144
Resize-Image $srcLogo "$root\res\mipmap-xxxhdpi\ic_launcher.png" 192 192
Resize-Image $srcLogo "$root\ic_launcher-playstore.png" 512 512
Resize-Image $srcLogo "$root\icon-playstore.png" 512 512
