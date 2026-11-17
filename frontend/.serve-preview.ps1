$ErrorActionPreference = 'Stop'
$siteRoot = (Resolve-Path '.\frontend').Path
$logPath = Join-Path $siteRoot '.serve-preview.log'
try {
  $listener = [System.Net.HttpListener]::new()
  $listener.Prefixes.Add('http://localhost:5500/')
  $listener.Start()
  "LISTENING $PID" | Set-Content -LiteralPath $logPath -Encoding UTF8
  $types = @{ '.html'='text/html; charset=utf-8'; '.css'='text/css; charset=utf-8'; '.js'='text/javascript; charset=utf-8'; '.svg'='image/svg+xml'; '.png'='image/png'; '.jpg'='image/jpeg'; '.jpeg'='image/jpeg'; '.webp'='image/webp'; '.ico'='image/x-icon' }
  while ($listener.IsListening) {
    $context = $listener.GetContext()
    try {
      $relative = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
      if ([string]::IsNullOrWhiteSpace($relative)) { $relative = 'index.html' }
      $candidate = [System.IO.Path]::GetFullPath((Join-Path $siteRoot $relative))
      $insideRoot = $candidate.StartsWith($siteRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or $candidate.Equals($siteRoot, [System.StringComparison]::OrdinalIgnoreCase)
      if (-not $insideRoot -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        $context.Response.StatusCode = 404
        $bytes = [System.Text.Encoding]::UTF8.GetBytes('Not found')
      } else {
        $extension = [System.IO.Path]::GetExtension($candidate).ToLowerInvariant()
        $context.Response.ContentType = $types[$extension]
        $bytes = [System.IO.File]::ReadAllBytes($candidate)
      }
      $context.Response.ContentLength64 = $bytes.Length
      $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    } finally { $context.Response.Close() }
  }
} catch { ($_ | Out-String) | Set-Content -LiteralPath $logPath -Encoding UTF8 }