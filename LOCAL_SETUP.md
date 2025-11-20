# Локальное развертывание OpenShift/Kubernetes для тестирования

Этот документ описывает несколько способов развернуть локальный OpenShift/Kubernetes кластер для тестирования сервиса управления подами.

## Вариант 1: OpenShift Local (CRC) - Рекомендуется для OpenShift

OpenShift Local (ранее CodeReady Containers) - это полноценный OpenShift кластер, который можно запустить локально.

### Требования
- Windows 10/11 с Hyper-V или WSL2
- Минимум 4 CPU, 9 GB RAM, 35 GB свободного места
- Docker Desktop или Podman

### Установка

1. **Скачайте OpenShift Local:**
   - Перейдите на https://developers.redhat.com/products/openshift-local/overview
   - Зарегистрируйтесь (бесплатно) и скачайте установщик для Windows
   - Скачайте образ OpenShift (pull-secret)

2. **Установите OpenShift Local:**
   ```powershell
   # Распакуйте архив и запустите установщик
   crc setup
   ```

3. **Настройте pull-secret:**
   ```powershell
   # Создайте файл pull-secret.txt с содержимым из Red Hat
   crc config set pull-secret-file C:\path\to\pull-secret.txt
   ```

4. **Запустите кластер:**
   ```powershell
   crc start
   ```
   
   Это займет 10-15 минут при первом запуске.

5. **Получите учетные данные:**
   ```powershell
   crc console --credentials
   ```
   
   Вы увидите:
   - URL консоли (обычно https://console-openshift-console.apps-crc.testing)
   - Логин/пароль для входа

6. **Настройте kubeconfig:**
   ```powershell
   # OpenShift Local автоматически настроит kubeconfig
   # Проверьте:
   kubectl config current-context
   # Должно быть: api-crc-testing:6443
   ```

### Использование с нашим сервисом

```powershell
# Установите переменные окружения
$env:OPENSHIFT_NAMESPACE = "default"
# kubeconfig уже настроен автоматически

# Запустите сервис
mvn spring-boot:run
```

---

## Вариант 2: Minikube - Простой Kubernetes кластер

Minikube - это простой способ запустить локальный Kubernetes кластер.

### Требования
- Windows 10/11
- Hyper-V, VirtualBox или Docker Desktop

### Установка

1. **Установите Minikube:**
   ```powershell
   # Скачайте с https://minikube.sigs.k8s.io/docs/start/
   # Или через Chocolatey:
   choco install minikube
   ```

2. **Установите kubectl:**
   ```powershell
   choco install kubernetes-cli
   # Или скачайте с https://kubernetes.io/docs/tasks/tools/
   ```

3. **Запустите Minikube:**
   ```powershell
   # С Hyper-V (требуются права администратора)
   minikube start --driver=hyperv
   
   # Или с Docker Desktop
   minikube start --driver=docker
   ```

4. **Проверьте подключение:**
   ```powershell
   kubectl get nodes
   kubectl get pods --all-namespaces
   ```

5. **Создайте тестовый namespace:**
   ```powershell
   kubectl create namespace test-namespace
   ```

### Создание тестового deployment для проверки

```powershell
# Создайте простой deployment
kubectl create deployment nginx-test --image=nginx -n test-namespace

# Проверьте поды
kubectl get pods -n test-namespace

# Используйте имя пода для тестирования нашего сервиса
```

### Использование с нашим сервисом

```powershell
# Minikube автоматически настроит kubeconfig
# Просто запустите сервис:
mvn spring-boot:run

# Или укажите namespace в application.yml:
# openshift.namespace: test-namespace
```

---

## Вариант 3: Kind (Kubernetes in Docker) - Легковесный вариант

Kind запускает Kubernetes кластер внутри Docker контейнеров.

### Требования
- Docker Desktop для Windows

### Установка

1. **Установите Kind:**
   ```powershell
   # Скачайте с https://kind.sigs.k8s.io/docs/user/quick-start/
   # Или через Chocolatey:
   choco install kind
   ```

2. **Создайте кластер:**
   ```powershell
   kind create cluster --name openshift-test
   ```

3. **Проверьте:**
   ```powershell
   kubectl cluster-info --context kind-openshift-test
   kubectl get nodes
   ```

4. **Создайте тестовый deployment:**
   ```powershell
   kubectl create deployment nginx-test --image=nginx
   kubectl get pods
   ```

### Использование с нашим сервисом

```powershell
# Kind автоматически настроит kubeconfig
mvn spring-boot:run
```

---

## Вариант 4: Docker Desktop с Kubernetes

Если у вас уже установлен Docker Desktop, можно использовать встроенный Kubernetes.

### Настройка

1. **Включите Kubernetes в Docker Desktop:**
   - Откройте Docker Desktop
   - Settings → Kubernetes
   - Включите "Enable Kubernetes"
   - Нажмите "Apply & Restart"

2. **Проверьте:**
   ```powershell
   kubectl config current-context
   # Должно быть: docker-desktop
   
   kubectl get nodes
   ```

3. **Создайте тестовый deployment:**
   ```powershell
   kubectl create deployment nginx-test --image=nginx
   kubectl get pods
   ```

---

## Быстрый старт для тестирования

### Шаг 1: Выберите вариант
- **Для OpenShift**: Вариант 1 (CRC)
- **Для быстрого теста**: Вариант 2 (Minikube) или Вариант 3 (Kind)

### Шаг 2: Запустите кластер
Следуйте инструкциям выбранного варианта.

### Шаг 3: Создайте тестовые поды

```powershell
# Создайте namespace
kubectl create namespace test

# Создайте deployment с несколькими репликами
kubectl create deployment nginx-app --image=nginx --replicas=3 -n test

# Проверьте поды
kubectl get pods -n test
```

### Шаг 4: Настройте и запустите наш сервис

```powershell
# Отредактируйте application.yml или установите переменные:
$env:OPENSHIFT_NAMESPACE = "test"

# Запустите сервис
mvn spring-boot:run
```

### Шаг 5: Протестируйте API

```powershell
# Получить список подов
curl http://localhost:8080/api/pods/test

# Получить информацию о конкретном поде (замените имя)
curl http://localhost:8080/api/pods/test/nginx-app-xxxxx-xxxxx

# Перезапустить под
curl -X POST http://localhost:8080/api/pods/test/nginx-app-xxxxx-xxxxx/restart

# Удалить под
curl -X DELETE http://localhost:8080/api/pods/test/nginx-app-xxxxx-xxxxx
```

---

## Проверка подключения

Перед запуском сервиса убедитесь, что подключение к кластеру работает:

```powershell
# Проверьте текущий контекст
kubectl config current-context

# Проверьте доступ к кластеру
kubectl cluster-info

# Проверьте список подов
kubectl get pods --all-namespaces

# Если все работает, можно запускать наш сервис
```

---

## Устранение проблем

### Проблема: "Unable to connect to the server"

**Решение:**
- Убедитесь, что кластер запущен (`minikube status` или `crc status`)
- Проверьте kubeconfig: `kubectl config view`

### Проблема: "Forbidden" или "Unauthorized"

**Решение:**
- Для Minikube/Kind обычно не требуется дополнительная настройка
- Для OpenShift Local используйте токен из `crc console --credentials`

### Проблема: Сервис не может подключиться к кластеру

**Решение:**
1. Проверьте, что kubeconfig находится в `~/.kube/config` (или путь в `KUBECONFIG`)
2. Проверьте права доступа к файлу kubeconfig
3. Убедитесь, что кластер доступен: `kubectl get nodes`

---

## Рекомендации

- **Для разработки и тестирования**: Используйте Minikube или Kind (быстрее, проще)
- **Для тестирования OpenShift-специфичных функций**: Используйте OpenShift Local (CRC)
- **Для минимальных ресурсов**: Используйте Kind или Docker Desktop Kubernetes

---

## Дополнительные ресурсы

- [OpenShift Local документация](https://developers.redhat.com/products/openshift-local/overview)
- [Minikube документация](https://minikube.sigs.k8s.io/docs/)
- [Kind документация](https://kind.sigs.k8s.io/)
- [Kubernetes документация](https://kubernetes.io/docs/)

