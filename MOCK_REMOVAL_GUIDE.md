# Руководство по удалению mock-заглушки

Этот документ содержит полный список всех изменений, связанных с mock-заглушкой для тестового кластера OpenShift. Используйте его для полного удаления этой функциональности из релизной версии.

## Дата добавления: 2025-11-20

---

## 📁 Новые файлы (нужно удалить)

### 1. MockDataService.java
**Путь:** `src/main/java/com/openshift/controller/service/MockDataService.java`
**Описание:** Сервис, предоставляющий тестовые данные (поды, deployments, namespaces) для mock-подключений.
**Действие:** Удалить файл полностью.

### 2. MOCK_CONNECTIONS.md
**Путь:** `MOCK_CONNECTIONS.md`
**Описание:** Документация по использованию mock-подключений.
**Действие:** Удалить файл полностью.

---

## 🔧 Измененные файлы (нужно откатить изменения)

### 1. OpenShiftConnection.java
**Путь:** `src/main/java/com/openshift/controller/entity/OpenShiftConnection.java`
**Изменения:**
- Добавлено поле `isMock` с аннотациями:
```java
/**
 * Является ли это подключение mock-заглушкой (для тестирования)
 */
@Column(nullable = false)
@Builder.Default
private Boolean isMock = false;
```
**Действие:** Удалить поле `isMock` полностью.

---

### 2. ConnectionService.java
**Путь:** `src/main/java/com/openshift/controller/service/ConnectionService.java`
**Изменения:**
- В методе `updateConnection()` добавлена строка:
```java
existing.setIsMock(updatedConnection.getIsMock() != null ? updatedConnection.getIsMock() : false);
```
**Действие:** Удалить эту строку из метода `updateConnection()`.

---

### 3. PodService.java
**Путь:** `src/main/java/com/openshift/controller/service/PodService.java`
**Изменения:**
- Добавлен импорт:
```java
import com.openshift.controller.entity.OpenShiftConnection;
```
- Добавлена зависимость:
```java
private final MockDataService mockDataService;
```
- Добавлен метод:
```java
private boolean isMockConnection() {
    return openShiftClientService.getActiveConnection()
            .map(OpenShiftConnection::getIsMock)
            .orElse(false);
}
```
- В методе `getAllPods()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для подов в namespace: {}", namespace);
    return mockDataService.getMockPods(namespace);
}
```
- В методе `getPod()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для пода {}/{}", namespace, podName);
    return mockDataService.getMockPod(namespace, podName);
}
```
- В методе `restartPod()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Mock-режим: операция перезапуска пода {}/{} выполнена (заглушка)", namespace, podName);
    return true;
}
```
- В методе `deletePod()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Mock-режим: операция удаления пода {}/{} выполнена (заглушка)", namespace, podName);
    return true;
}
```
- В методе `getPodsByLabel()` заменена логика на проверку mock-режима:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для поиска подов с селектором: {}", labelSelector);
    List<PodInfo> allPods = mockDataService.getMockPods(namespace);
    // ... фильтрация по селектору
    return ...;
}
```
**Действие:** 
- Удалить импорт `OpenShiftConnection`
- Удалить зависимость `MockDataService mockDataService`
- Удалить метод `isMockConnection()`
- Удалить все проверки `if (isMockConnection())` и восстановить оригинальную логику

---

### 4. NamespaceService.java
**Путь:** `src/main/java/com/openshift/controller/service/NamespaceService.java`
**Изменения:**
- Добавлен импорт:
```java
import com.openshift.controller.entity.OpenShiftConnection;
```
- Добавлена зависимость:
```java
private final MockDataService mockDataService;
```
- Добавлен метод:
```java
private boolean isMockConnection() {
    return openShiftClientService.getActiveConnection()
            .map(OpenShiftConnection::getIsMock)
            .orElse(false);
}
```
- В методе `getAllNamespaces()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для namespaces");
    return mockDataService.getMockNamespaces();
}
```
**Действие:**
- Удалить импорт `OpenShiftConnection`
- Удалить зависимость `MockDataService mockDataService`
- Удалить метод `isMockConnection()`
- Удалить проверку `if (isMockConnection())` из метода `getAllNamespaces()`

---

### 5. DeploymentService.java
**Путь:** `src/main/java/com/openshift/controller/service/DeploymentService.java`
**Изменения:**
- Добавлен импорт:
```java
import com.openshift.controller.entity.OpenShiftConnection;
```
- Добавлена зависимость:
```java
private final MockDataService mockDataService;
```
- Добавлен метод:
```java
private boolean isMockConnection() {
    return openShiftClientService.getActiveConnection()
            .map(OpenShiftConnection::getIsMock)
            .orElse(false);
}
```
- В методе `getAllDeployments()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для deployments в namespace: {}", namespace);
    return mockDataService.getMockDeployments(namespace);
}
```
- В методе `getDeployment()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Использование mock-данных для deployment {}/{}", namespace, name);
    return mockDataService.getMockDeployment(namespace, name);
}
```
- В методе `scaleDeployment()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Mock-режим: операция масштабирования deployment {}/{} до {} реплик выполнена (заглушка)", 
            namespace, name, replicas);
    return true;
}
```
- В методе `restartDeployment()` добавлена проверка в начале:
```java
if (isMockConnection()) {
    log.info("Mock-режим: операция перезапуска deployment {}/{} выполнена (заглушка)", namespace, name);
    return true;
}
```
**Действие:**
- Удалить импорт `OpenShiftConnection`
- Удалить зависимость `MockDataService mockDataService`
- Удалить метод `isMockConnection()`
- Удалить все проверки `if (isMockConnection())` из методов

---

### 6. OpenShiftClientService.java
**Путь:** `src/main/java/com/openshift/controller/service/OpenShiftClientService.java`
**Изменения:**
- В методе `getClient()` добавлена проверка перед созданием клиента:
```java
// Если клиент уже создан для этого подключения, возвращаем его
if (cachedClient != null && cachedConnection != null && 
    cachedConnection.getId().equals(conn.getId())) {
    return Optional.of(cachedClient);
}

// Для mock-подключений возвращаем пустой Optional
if (conn.getIsMock() != null && conn.getIsMock()) {
    return Optional.empty();
}
```
- В методе `getClient()` добавлена проверка после получения подключения:
```java
OpenShiftConnection conn = connection.get();

// Для mock-подключений не создаем реальный клиент
if (conn.getIsMock() != null && conn.getIsMock()) {
    log.info("Mock-подключение обнаружено: {} (ID: {}) - реальный клиент не создается", 
            conn.getName(), conn.getId());
    // Возвращаем пустой Optional, но сохраняем информацию о подключении
    cachedConnection = conn;
    return Optional.empty();
}
```
**Действие:**
- Удалить проверку `if (conn.getIsMock() != null && conn.getIsMock())` из обоих мест в методе `getClient()`

---

### 7. ConnectionController.java
**Путь:** `src/main/java/com/openshift/controller/controller/ConnectionController.java`
**Изменения:**
- В методе `saveConnection()` добавлен параметр:
```java
@RequestParam(required = false, defaultValue = "false") boolean isMock,
```
- В методе `saveConnection()` добавлена установка флага:
```java
.isMock(isMock)
```
- В методе `saveConnection()` добавлена проверка для mock-подключений:
```java
// Для mock-подключений не проверяем реальное соединение
if (isMock) {
    redirectAttributes.addFlashAttribute("success", 
        "Mock-подключение успешно сохранено и активировано! Используются тестовые данные.");
} else {
    // проверка подключения
}
```
- Добавлен новый метод `createMockConnection()`:
```java
@PostMapping("/create-mock")
public String createMockConnection(RedirectAttributes redirectAttributes) {
    // ... весь метод
}
```
- В методе `updateConnection()` добавлен параметр:
```java
@RequestParam(required = false, defaultValue = "false") boolean isMock,
```
- В методе `updateConnection()` добавлена установка флага:
```java
.isMock(isMock)
```
- В методе `updateConnection()` добавлена проверка:
```java
// Для mock-подключений не проверяем реальное соединение
if (!isMock) {
    // проверка подключения
}
```
**Действие:**
- Удалить параметр `isMock` из метода `saveConnection()`
- Удалить `.isMock(isMock)` из метода `saveConnection()`
- Удалить проверку `if (isMock)` и объединить логику
- Удалить метод `createMockConnection()` полностью
- Удалить параметр `isMock` из метода `updateConnection()`
- Удалить `.isMock(isMock)` из метода `updateConnection()`
- Удалить проверку `if (!isMock)` из метода `updateConnection()`

---

### 8. connection-setup.html
**Путь:** `src/main/resources/templates/connection-setup.html`
**Изменения:**
- В списке подключений добавлен бейдж для mock-подключений:
```html
<span th:if="${conn.isMock}" class="badge" style="background: #ed8936;">Mock</span>
```
- Добавлен блок для создания тестового подключения:
```html
<!-- Кнопка для быстрого создания тестового подключения -->
<div style="margin-bottom: 25px; padding: 15px; background: #feebc8; border-radius: 8px; border-left: 4px solid #ed8936;">
    <strong>🧪 Для тестирования:</strong> Создайте тестовое mock-подключение с предзаполненными данными
    <form th:action="@{/connection/create-mock}" method="post" style="margin-top: 10px;">
        <button type="submit" class="btn btn-warning">Создать тестовое подключение</button>
    </form>
</div>
```
- В форме добавлен чекбокс для mock-подключения:
```html
<div class="form-group">
    <label style="display: flex; align-items: center; cursor: pointer;">
        <input type="checkbox" name="isMock" value="true" 
               th:checked="${editingConnection?.isMock}"
               style="width: auto; margin-right: 8px;">
        <span>Это mock-подключение (для тестирования, использует тестовые данные вместо реального кластера)</span>
    </label>
    <small>Mock-подключение позволяет протестировать интерфейс без реального OpenShift кластера</small>
</div>
```
**Действие:**
- Удалить бейдж `<span th:if="${conn.isMock}" ...>`
- Удалить весь блок "Для тестирования" с кнопкой
- Удалить блок с чекбоксом `isMock`

---

### 9. README.md
**Путь:** `README.md`
**Изменения:**
- В разделе "Управление подключениями" добавлена строка:
```markdown
- ✅ **Mock-подключения для тестирования** - создание тестовых подключений с заглушками данных
```
- Добавлен новый раздел "### 3. Тестовые mock-подключения":
```markdown
### 3. Тестовые mock-подключения

Для визуального тестирования интерфейса без реального OpenShift кластера можно создать mock-подключение:
...
```
**Действие:**
- Удалить строку про mock-подключения из списка возможностей
- Удалить весь раздел "### 3. Тестовые mock-подключения"

---

## 🗄️ Изменения в базе данных

### Таблица: openshift_connections
**Столбец:** `is_mock`
**Тип:** `BOOLEAN NOT NULL DEFAULT false`
**Описание:** Флаг для обозначения mock-подключений

**SQL для удаления:**
```sql
ALTER TABLE openshift_connections DROP COLUMN IF EXISTS is_mock;
```

**Скрипт для удаления:** `remove-mock-column.sql` (создан ниже)

**Действие:** Выполнить SQL команду для удаления столбца из базы данных.

---

## 📝 Дополнительные файлы (опционально, можно оставить)

### add-mock-column.sql
**Путь:** `add-mock-column.sql`
**Описание:** SQL скрипт для добавления столбца is_mock (использовался для миграции)
**Действие:** Можно удалить, если больше не нужен.

---

## ✅ Чеклист для удаления

### Фаза 1: Удаление файлов
- [ ] Удалить `src/main/java/com/openshift/controller/service/MockDataService.java`
- [ ] Удалить `MOCK_CONNECTIONS.md`
- [ ] (Опционально) Удалить `add-mock-column.sql`

### Фаза 2: Откат изменений в Java классах
- [ ] `OpenShiftConnection.java` - удалить поле `isMock`
- [ ] `ConnectionService.java` - удалить строку с `setIsMock()`
- [ ] `PodService.java` - удалить все mock-проверки и зависимости
- [ ] `NamespaceService.java` - удалить все mock-проверки и зависимости
- [ ] `DeploymentService.java` - удалить все mock-проверки и зависимости
- [ ] `OpenShiftClientService.java` - удалить проверки mock-подключений
- [ ] `ConnectionController.java` - удалить параметры `isMock` и метод `createMockConnection()`

### Фаза 3: Откат изменений в шаблонах
- [ ] `connection-setup.html` - удалить бейдж, кнопку и чекбокс mock

### Фаза 4: Откат изменений в документации
- [ ] `README.md` - удалить упоминания о mock-подключениях

### Фаза 5: Изменения в базе данных
- [ ] Выполнить SQL для удаления столбца `is_mock`

### Фаза 6: Тестирование
- [ ] Убедиться, что приложение компилируется
- [ ] Убедиться, что приложение запускается
- [ ] Проверить работу подключений (без mock)
- [ ] Проверить все операции с подами и deployments

---

## 🚨 Важные замечания

1. **Порядок удаления:** Сначала удалите код и файлы, затем обновите базу данных.
2. **Резервная копия:** Рекомендуется сделать резервную копию базы данных перед удалением столбца.
3. **Существующие mock-подключения:** Если в базе есть mock-подключения (с `is_mock = true`), их нужно либо удалить, либо преобразовать в обычные подключения перед удалением столбца.
4. **Тестирование:** После удаления обязательно протестируйте все функции, чтобы убедиться, что ничего не сломалось.

---

## 📌 Быстрая команда для удаления столбца из БД

```sql
-- Удаление столбца is_mock из таблицы openshift_connections
ALTER TABLE openshift_connections DROP COLUMN IF EXISTS is_mock;
```

Или выполнить через psql:
```bash
psql -U postgres -d openshift_controller -c "ALTER TABLE openshift_connections DROP COLUMN IF EXISTS is_mock;"
```

