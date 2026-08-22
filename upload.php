<?php
declare(strict_types=1);

/*
 * Fire Manager image upload endpoint.
 *
 * Upload this file to:
 *   https://smmnoon.com/fire/upload.php
 *
 * Create a folder beside it named:
 *   uploads
 *
 * Make sure the uploads folder is writable by PHP.
 */

const UPLOAD_TOKEN = 'CHANGE_THIS_TO_A_LONG_SECRET_TOKEN';
const MAX_FILE_SIZE = 8 * 1024 * 1024; // 8 MB
const UPLOAD_DIR = __DIR__ . '/uploads';

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Upload-Token');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

function respond(int $code, array $payload): void
{
    http_response_code($code);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function clean_part(string $value): string
{
    $value = preg_replace('/[^A-Za-z0-9_-]+/', '_', $value);
    $value = trim((string) $value, '_');
    return $value !== '' ? $value : 'unknown';
}

function base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && $_SERVER['HTTP_X_FORWARDED_PROTO'] === 'https');
    $scheme = $https ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'smmnoon.com';
    $scriptDir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/fire/upload.php')), '/');
    return $scheme . '://' . $host . $scriptDir;
}

function stamp_image(string $path, string $mime): void
{
    if (!extension_loaded('gd')) {
        return;
    }

    if ($mime === 'image/jpeg') {
        $image = @imagecreatefromjpeg($path);
    } elseif ($mime === 'image/png') {
        $image = @imagecreatefrompng($path);
    } elseif ($mime === 'image/webp' && function_exists('imagecreatefromwebp')) {
        $image = @imagecreatefromwebp($path);
    } else {
        return;
    }

    if (!$image) return;

    $text = date('Y-m-d H:i:s');
    $width = imagesx($image);
    $height = imagesy($image);
    $font = 5;
    $textWidth = imagefontwidth($font) * strlen($text);
    $textHeight = imagefontheight($font);
    $padding = 10;
    $x = max($padding, $width - $textWidth - ($padding * 2));
    $y = max($padding, $height - $textHeight - ($padding * 2));
    $bg = imagecolorallocatealpha($image, 0, 0, 0, 45);
    $fg = imagecolorallocate($image, 255, 255, 255);

    imagefilledrectangle($image, $x - $padding, $y - $padding, $x + $textWidth + $padding, $y + $textHeight + $padding, $bg);
    imagestring($image, $font, $x, $y, $text, $fg);

    if ($mime === 'image/jpeg') {
        imagejpeg($image, $path, 90);
    } elseif ($mime === 'image/png') {
        imagepng($image, $path, 6);
    } elseif ($mime === 'image/webp' && function_exists('imagewebp')) {
        imagewebp($image, $path, 90);
    }
    imagedestroy($image);
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['ok' => false, 'error' => 'POST only']);
}

$token = $_SERVER['HTTP_X_UPLOAD_TOKEN'] ?? ($_POST['token'] ?? '');
if (!hash_equals(UPLOAD_TOKEN, (string) $token)) {
    respond(401, ['ok' => false, 'error' => 'Invalid upload token']);
}

if (!isset($_FILES['file'])) {
    respond(400, ['ok' => false, 'error' => 'Missing file field']);
}

$file = $_FILES['file'];
if (!is_array($file) || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    respond(400, ['ok' => false, 'error' => 'Upload failed']);
}

if (($file['size'] ?? 0) <= 0 || ($file['size'] ?? 0) > MAX_FILE_SIZE) {
    respond(413, ['ok' => false, 'error' => 'File is too large']);
}

if (!is_dir(UPLOAD_DIR) && !mkdir(UPLOAD_DIR, 0755, true)) {
    respond(500, ['ok' => false, 'error' => 'Cannot create uploads folder']);
}

$finfo = new finfo(FILEINFO_MIME_TYPE);
$mime = $finfo->file($file['tmp_name']);
$allowed = [
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
];

if (!isset($allowed[$mime])) {
    respond(415, ['ok' => false, 'error' => 'Only JPG, PNG, and WEBP images are allowed']);
}

$team = clean_part((string) ($_POST['team_code'] ?? 'team'));
$customer = clean_part((string) ($_POST['customer'] ?? 'customer'));
$device = clean_part((string) ($_POST['device'] ?? 'device'));
$extension = $allowed[$mime];
$fileName = $team . '_' . $customer . '_' . $device . '_' . date('Ymd_His') . '_' . bin2hex(random_bytes(5)) . '.' . $extension;
$targetPath = UPLOAD_DIR . '/' . $fileName;

if (!move_uploaded_file($file['tmp_name'], $targetPath)) {
    respond(500, ['ok' => false, 'error' => 'Cannot save uploaded file']);
}

chmod($targetPath, 0644);
stamp_image($targetPath, $mime);

$url = base_url() . '/uploads/' . rawurlencode($fileName);
respond(200, [
    'ok' => true,
    'url' => $url,
    'file_name' => $fileName,
    'mime' => $mime,
    'size' => (int) $file['size'],
]);
