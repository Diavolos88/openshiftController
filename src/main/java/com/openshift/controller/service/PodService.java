package com.openshift.controller.service;

import com.openshift.controller.dto.PodInfo;
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

    /**
     * Получить список всех подов в указанном namespace
     * 
     * @param namespace namespace для поиска подов
     * @return список информации о подах
     */
    public List<PodInfo> getAllPods(String namespace) {
        log.info("Получение списка подов в namespace: {}", namespace);
        
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

