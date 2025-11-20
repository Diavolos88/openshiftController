# Скрипт для запуска сервиса OpenShift Controller
# Добавляет Maven в PATH и запускает сервис

Write-Host "Настройка Maven..." -ForegroundColor Yellow

# Добавляем Maven в PATH для текущей сессии
$mavenPath = "C:\Program Files\Java\apache-maven-3.9.11\bin"
if ($env:PATH -notlike "*$mavenPath*") {
    $env:PATH = "$mavenPath;$env:PATH"
    Write-Host "✅ Maven добавлен в PATH" -ForegroundColor Green
}

# Проверяем Maven
Write-Host "`nПроверка Maven..." -ForegroundColor Yellow
try {
    $mvnVersion = mvn -version 2>&1 | Select-Object -First 1
    Write-Host "✅ $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Ошибка: Maven не найден" -ForegroundColor Red
    exit 1
}

# Проверяем конфигурацию
Write-Host "`nПроверка конфигурации..." -ForegroundColor Yellow
if (Test-Path "src\main\resources\application.yml") {
    Write-Host "✅ application.yml найден" -ForegroundColor Green
} else {
    Write-Host "❌ application.yml не найден" -ForegroundColor Red
    exit 1
}

# Запускаем сервис
Write-Host "`n🚀 Запуск сервиса OpenShift Controller..." -ForegroundColor Cyan
Write-Host "Сервис будет доступен на: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Для остановки нажмите Ctrl+C`n" -ForegroundColor Yellow

mvn spring-boot:run

