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
import java.util.List;
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

