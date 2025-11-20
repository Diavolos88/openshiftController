# Исправление кодировки в логах

## Проблема
В логах отображаются кракозябры вместо русских букв из-за неправильной кодировки консоли Windows.

## Решение

### 1. Настройки в коде (уже добавлены)
- В `OpenShiftControllerApplication.java` установлена UTF-8 кодировка
- В `application.yml` настроена кодировка для логирования

### 2. Настройка PowerShell для UTF-8

Перед запуском сервиса выполните в PowerShell:

```powershell
# Установить кодировку UTF-8 для текущей сессии
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001

# Затем запустите сервис
$env:PATH = "C:\Program Files\Java\apache-maven-3.9.11\bin;$env:PATH"
mvn spring-boot:run
```

### 3. Постоянная настройка PowerShell

Чтобы каждый раз не настраивать кодировку, добавьте в профиль PowerShell:

```powershell
# Откройте профиль
notepad $PROFILE

# Добавьте строки:
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
```

### 4. Альтернатива: использовать Windows Terminal

Windows Terminal лучше поддерживает UTF-8. Установите из Microsoft Store.

## Проверка

После настройки русские символы в логах должны отображаться корректно:
- ✅ "Получение списка подов в namespace: diavolos88-dev"
- ✅ "Под diavolos88-dev/ingress-859579957c-rfvqr успешно удален"

