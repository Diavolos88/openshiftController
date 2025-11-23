package com.openshift.controller.service;

import com.openshift.controller.dto.PodInfo;
import com.openshift.controller.entity.OpenShiftConnection;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для управления подами в OpenShift
 * 
 * Предоставляет методы для:
 * - Получения списка подов
 * - Получения информации о конкретном поде
 * - Перезапуска подов (удаление и автоматическое пересоздание через deployment)
 * - Удаления подов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private final OpenShiftClientService openShiftClientService;
    private final MockDataService mockDataService;
    private final PodStartupTimeService podStartupTimeService;
    private final ConnectionService connectionService;

    /**
     * Проверить, является ли активное подключение mock-заглушкой
     */
    private boolean isMockConnection() {
        return openShiftClientService.getActiveConnection()
                .map(OpenShiftConnection::getIsMock)
                .orElse(false);
    }

    /**
     * Получить список всех подов в указанном namespace
     * 
     * @param namespace namespace для поиска подов
     * @return список информации о подах
     */
    public List<PodInfo> getAllPods(String namespace) {
        log.info("Получение списка подов в namespace: {}", namespace);
        
        // Если подключение - mock, возвращаем mock-данные
        if (isMockConnection()) {
            log.info("Использование mock-данных для подов в namespace: {}", namespace);
            return mockDataService.getMockPods(namespace);
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
        
        PodList podList = openShiftClient.pods()
                .inNamespace(namespace)
                .list();
        
        return podList.getItems().stream()
                .map(this::mapToPodInfo)
                .collect(Collectors.toList());
    }

    /**
     * Получить информацию о конкретном поде
     * 
     * @param namespace namespace, где находится под
     * @param podName имя пода
     * @return информация о поде или null, если под не найден
     */
    public PodInfo getPod(String namespace, String podName) {
        log.info("Получение информации о поде {}/{}", namespace, podName);
        
        // Если подключение - mock, возвращаем mock-данные
        if (isMockConnection()) {
            log.info("Использование mock-данных для пода {}/{}", namespace, podName);
            return mockDataService.getMockPod(namespace, podName);
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
        
        Pod pod = openShiftClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .get();
        
        if (pod == null) {
            log.warn("Под {}/{} не найден", namespace, podName);
            return null;
        }
        
        return mapToPodInfo(pod);
    }

    /**
     * Перезапустить под
     * 
     * В OpenShift/Kubernetes перезапуск пода выполняется через его удаление.
     * Если под управляется через Deployment, ReplicaSet автоматически создаст новый под.
     * 
     * @param namespace namespace, где находится под
     * @param podName имя пода для перезапуска
     * @return true, если операция успешна
     */
    public boolean restartPod(String namespace, String podName) {
        log.info("Перезапуск пода {}/{}", namespace, podName);
        
        // Для mock-подключений просто логируем
        if (isMockConnection()) {
            log.info("Mock-режим: операция перезапуска пода {}/{} выполнена (заглушка)", namespace, podName);
            return true;
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
        
        try {
            Pod pod = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .get();
            
            if (pod == null) {
                log.error("Под {}/{} не найден", namespace, podName);
                return false;
            }
            
            // Удаляем под - если он управляется через Deployment, он будет автоматически пересоздан
            boolean deleted = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .delete()
                    .size() > 0;
            
            if (deleted) {
                log.info("Под {}/{} успешно удален, будет пересоздан если управляется через Deployment", 
                        namespace, podName);
                return true;
            } else {
                log.warn("Не удалось удалить под {}/{}", namespace, podName);
                return false;
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске пода {}/{}", namespace, podName, e);
            throw new RuntimeException("Ошибка при перезапуске пода: " + e.getMessage(), e);
        }
    }

    /**
     * Удалить под полностью
     * 
     * @param namespace namespace, где находится под
     * @param podName имя пода для удаления
     * @return true, если операция успешна
     */
    public boolean deletePod(String namespace, String podName) {
        log.info("Удаление пода {}/{}", namespace, podName);
        
        // Для mock-подключений просто логируем
        if (isMockConnection()) {
            log.info("Mock-режим: операция удаления пода {}/{} выполнена (заглушка)", namespace, podName);
            return true;
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
        
        try {
            boolean deleted = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .delete()
                    .size() > 0;
            
            if (deleted) {
                log.info("Под {}/{} успешно удален", namespace, podName);
                return true;
            } else {
                log.warn("Под {}/{} не найден или уже удален", namespace, podName);
                return false;
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении пода {}/{}", namespace, podName, e);
            throw new RuntimeException("Ошибка при удалении пода: " + e.getMessage(), e);
        }
    }

    /**
     * Получить поды по label selector
     * 
     * @param namespace namespace для поиска
     * @param labelSelector строка селектора (например, "app=myapp")
     * @return список подов, соответствующих селектору
     */
    public List<PodInfo> getPodsByLabel(String namespace, String labelSelector) {
        log.info("Поиск подов в namespace {} с селектором: {}", namespace, labelSelector);
        
        // Для mock-подключений фильтруем по селектору из всех mock-подов
        if (isMockConnection()) {
            log.info("Использование mock-данных для поиска подов с селектором: {}", labelSelector);
            List<PodInfo> allPods = mockDataService.getMockPods(namespace);
            // Простая фильтрация по селектору (формат: "key=value" или "key")
            return allPods.stream()
                    .filter(pod -> {
                        if (pod.getLabels() == null) return false;
                        String[] parts = labelSelector.split("=");
                        if (parts.length == 2) {
                            return parts[1].equals(pod.getLabels().get(parts[0]));
                        } else if (parts.length == 1) {
                            return pod.getLabels().containsKey(parts[0]);
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
        
        PodList podList = openShiftClient.pods()
                .inNamespace(namespace)
                .withLabelSelector(labelSelector)
                .list();
        
        return podList.getItems().stream()
                .map(this::mapToPodInfo)
                .collect(Collectors.toList());
    }

    /**
     * Парсинг строки с timestamp в Instant
     */
    private Instant parseCreationTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            // Kubernetes использует RFC3339 формат
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            log.warn("Не удалось распарсить timestamp: {}", timestamp, e);
            return null;
        }
    }

    /**
     * Массовое удаление подов
     * 
     * @param namespace namespace, где находятся поды
     * @param podNames список имен подов для удаления
     * @return карта результатов (имя пода -> успех операции)
     */
    public java.util.Map<String, Boolean> deletePods(String namespace, List<String> podNames) {
        log.info("Массовое удаление {} подов в namespace: {}", podNames.size(), namespace);
        return podNames.stream()
                .collect(java.util.stream.Collectors.toMap(
                        podName -> podName,
                        podName -> {
                            try {
                                return deletePod(namespace, podName);
                            } catch (Exception e) {
                                log.error("Ошибка при удалении пода {}/{}", namespace, podName, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Массовый перезапуск подов
     * 
     * @param namespace namespace, где находятся поды
     * @param podNames список имен подов для перезапуска
     * @return карта результатов (имя пода -> успех операции)
     */
    public java.util.Map<String, Boolean> restartPods(String namespace, List<String> podNames) {
        log.info("Массовый перезапуск {} подов в namespace: {}", podNames.size(), namespace);
        return podNames.stream()
                .collect(java.util.stream.Collectors.toMap(
                        podName -> podName,
                        podName -> {
                            try {
                                return restartPod(namespace, podName);
                            } catch (Exception e) {
                                log.error("Ошибка при перезапуске пода {}/{}", namespace, podName, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Замерить время старта пода для deployment
     * 
     * Алгоритм:
     * 1. Удаляет один под из deployment
     * 2. Ожидает, пока новый под полностью стартует (Running + Ready)
     * 3. Получает полные детали нового пода (pod details)
     * 4. Извлекает из conditions время Initialized и PodReadyToStartContainers
     * 5. Вычисляет разницу между этими временами
     * 
     * @param connectionId ID подключения
     * @param namespace namespace deployment
     * @param deploymentName имя deployment
     * @return время старта в секундах (разница между PodReadyToStartContainers и Initialized) или null, если не удалось замерить
     */
    public Long measurePodStartupTime(Long connectionId, String namespace, String deploymentName) {
        log.info("Замер времени старта пода для deployment {}/{} для подключения ID: {}", namespace, deploymentName, connectionId);
        
        // Для mock-подключений возвращаем тестовое значение
        if (connectionService.getConnectionById(connectionId)
                .map(OpenShiftConnection::getIsMock)
                .orElse(false)) {
            log.info("Mock-режим: возвращаем тестовое время старта 5 секунд");
            podStartupTimeService.saveStartupTime(connectionId, namespace, deploymentName, 5L);
            return 5L;
        }
        
        OpenShiftClient openShiftClient = openShiftClientService.getClientForConnection(connectionId)
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено"));
        
        try {
            // Получаем deployment для получения label selector
            io.fabric8.kubernetes.api.model.apps.Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();
            
            if (deployment == null) {
                log.error("Deployment {}/{} не найден", namespace, deploymentName);
                return null;
            }
            
            // Получаем label selector из deployment
            String labelSelector;
            if (deployment.getSpec().getSelector().getMatchLabels() != null && 
                !deployment.getSpec().getSelector().getMatchLabels().isEmpty()) {
                labelSelector = deployment.getSpec().getSelector().getMatchLabels().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(","));
            } else {
                // Если нет labels, используем имя deployment
                labelSelector = "app=" + deploymentName;
            }
            
            // Получаем поды для этого deployment
            PodList podList = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withLabelSelector(labelSelector)
                    .list();
            
            if (podList.getItems().isEmpty()) {
                log.warn("Не найдено подов для deployment {}/{}", namespace, deploymentName);
                return null;
            }
            
            // Выбираем первый под для удаления
            Pod podToDelete = podList.getItems().get(0);
            String podNameToDelete = podToDelete.getMetadata().getName();
            
            log.info("Удаляем под {}/{} для замера времени старта", namespace, podNameToDelete);
            
            // Удаляем под
            boolean deleted = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withName(podNameToDelete)
                    .delete()
                    .size() > 0;
            
            if (!deleted) {
                log.error("Не удалось удалить под {}/{}", namespace, podNameToDelete);
                return null;
            }
            
            log.info("Под {}/{} удален. Ожидаем создания нового пода для deployment {}/{}", 
                    namespace, podNameToDelete, namespace, deploymentName);
            
            int maxWaitTime = 300; // Максимальное время ожидания 5 минут
            int checkInterval = 2; // Проверка каждые 2 секунды
            String newPodName = null;
            
            // Ожидаем, пока новый под полностью стартует
            for (int i = 0; i < maxWaitTime / checkInterval; i++) {
                try {
                    Thread.sleep(checkInterval * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Прервано ожидание готовности пода", e);
                    return null;
                }
                
                // Получаем список подов снова
                PodList currentPods = openShiftClient.pods()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list();
                
                // Ищем новый под (не тот, что удалили) и проверяем его готовность
                for (Pod pod : currentPods.getItems()) {
                    String currentPodName = pod.getMetadata() != null ? pod.getMetadata().getName() : null;
                    if (currentPodName != null && !currentPodName.equals(podNameToDelete)) {
                        // Это новый под - проверяем его готовность
                        if (pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())) {
                            // Проверяем, что все контейнеры готовы
                            if (pod.getStatus().getContainerStatuses() != null && 
                                !pod.getStatus().getContainerStatuses().isEmpty()) {
                                boolean allReady = pod.getStatus().getContainerStatuses().stream()
                                        .allMatch(status -> status != null && 
                                                 status.getReady() != null && status.getReady());
                                if (allReady) {
                                    // Проверяем, что условия Ready и ContainersReady установлены
                                    boolean isReady = pod.getStatus().getConditions() != null &&
                                            pod.getStatus().getConditions().stream()
                                                    .anyMatch(condition -> condition != null &&
                                                            "Ready".equals(condition.getType()) &&
                                                            "True".equals(condition.getStatus()));
                                    if (isReady) {
                                        newPodName = currentPodName;
                                        log.info("Под {}/{} полностью готов. Получаем детали для замера времени старта", 
                                                namespace, newPodName);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (newPodName != null) {
                    break;
                }
            }
            
            if (newPodName == null) {
                log.warn("Превышено время ожидания готовности пода для deployment {}/{}", namespace, deploymentName);
                return null;
            }
            
            // Получаем полную информацию о новом поде (pod details)
            Pod newPod = openShiftClient.pods()
                    .inNamespace(namespace)
                    .withName(newPodName)
                    .get();
            
            if (newPod == null || newPod.getStatus() == null || newPod.getStatus().getConditions() == null) {
                log.error("Не удалось получить детали пода {}/{} или отсутствуют условия", namespace, newPodName);
                return null;
            }
            
            // Ищем Initialized и PodReadyToStartContainers условия
            Optional<Instant> initializedTime = newPod.getStatus().getConditions().stream()
                    .filter(condition -> condition != null && "Initialized".equals(condition.getType()) && "True".equals(condition.getStatus()))
                    .map(condition -> {
                        String lastTransitionTimeStr = condition.getLastTransitionTime();
                        if (lastTransitionTimeStr != null && !lastTransitionTimeStr.isEmpty()) {
                            try {
                                return Instant.parse(lastTransitionTimeStr);
                            } catch (Exception e) {
                                log.warn("Не удалось распарсить lastTransitionTime для Initialized: {}", lastTransitionTimeStr, e);
                                return null;
                            }
                        }
                        return null;
                    })
                    .filter(time -> time != null)
                    .findFirst();
            
            Optional<Instant> podReadyToStartContainersTime = newPod.getStatus().getConditions().stream()
                    .filter(condition -> condition != null && "PodReadyToStartContainers".equals(condition.getType()) && "True".equals(condition.getStatus()))
                    .map(condition -> {
                        String lastTransitionTimeStr = condition.getLastTransitionTime();
                        if (lastTransitionTimeStr != null && !lastTransitionTimeStr.isEmpty()) {
                            try {
                                return Instant.parse(lastTransitionTimeStr);
                            } catch (Exception e) {
                                log.warn("Не удалось распарсить lastTransitionTime для PodReadyToStartContainers: {}", lastTransitionTimeStr, e);
                                return null;
                            }
                        }
                        return null;
                    })
                    .filter(time -> time != null)
                    .findFirst();
            
            if (!initializedTime.isPresent() || !podReadyToStartContainersTime.isPresent()) {
                log.warn("Не найдены необходимые условия для замера времени старта пода {}/{}", namespace, newPodName);
                log.debug("Initialized: {}, PodReadyToStartContainers: {}", 
                        initializedTime.isPresent(), podReadyToStartContainersTime.isPresent());
                return null;
            }
            
            // Вычисляем разницу между PodReadyToStartContainers и Initialized
            long startupTimeSeconds = podReadyToStartContainersTime.get().getEpochSecond() - 
                                     initializedTime.get().getEpochSecond();
            
            log.info("Под {}/{} готов. Время старта (PodReadyToStartContainers - Initialized): {} секунд", 
                    namespace, newPodName, startupTimeSeconds);
            
            // Сохраняем время старта в БД
            podStartupTimeService.saveStartupTime(connectionId, namespace, deploymentName, startupTimeSeconds);
            
            return startupTimeSeconds;
            
        } catch (Exception e) {
            log.error("Ошибка при замере времени старта пода для deployment {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Ошибка при замере времени старта: " + e.getMessage(), e);
        }
    }

    /**
     * Замерить время старта подов для нескольких Deployments
     * 
     * @param connectionId ID подключения
     * @param namespace namespace deployments
     * @param deploymentNames список имен deployments
     * @return Map с результатами: имя deployment -> время старта в секундах (или null, если не удалось)
     */
    public Map<String, Long> measurePodStartupTimeForDeployments(
            Long connectionId, String namespace, List<String> deploymentNames) {
        log.info("Замер времени старта подов для {} deployments в namespace {} для подключения ID: {}", 
                deploymentNames.size(), namespace, connectionId);
        
        Map<String, Long> results = new HashMap<>();
        
        for (String deploymentName : deploymentNames) {
            try {
                log.info("Замер времени старта для deployment {}/{}", namespace, deploymentName);
                Long startupTime = measurePodStartupTime(connectionId, namespace, deploymentName);
                results.put(deploymentName, startupTime);
            } catch (Exception e) {
                log.error("Ошибка при замере времени старта для deployment {}/{}", namespace, deploymentName, e);
                results.put(deploymentName, null);
            }
        }
        
        log.info("Завершен замер времени старта подов. Успешно: {}/{}", 
                results.values().stream().filter(t -> t != null).count(), deploymentNames.size());
        
        return results;
    }

    /**
     * Маппинг Pod в PodInfo DTO
     */
    private PodInfo mapToPodInfo(Pod pod) {
        return PodInfo.builder()
                .name(pod.getMetadata().getName())
                .namespace(pod.getMetadata().getNamespace())
                .status(pod.getStatus().getPhase())
                .nodeName(pod.getSpec().getNodeName())
                .creationTimestamp(parseCreationTimestamp(pod.getMetadata().getCreationTimestamp()))
                .labels(pod.getMetadata().getLabels())
                .annotations(pod.getMetadata().getAnnotations())
                .build();
    }
}

