package com.openshift.controller.service;

import com.openshift.controller.dto.DeploymentInfo;
import com.openshift.controller.entity.OpenShiftConnection;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для управления Deployments в OpenShift
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final OpenShiftClientService openShiftClientService;
    private final ConnectionService connectionService;
    private final StateService stateService;
    private final MockDataService mockDataService;
    
    /**
     * Проверить, является ли подключение mock-заглушкой
     */
    private boolean isMockConnection(Long connectionId) {
        return connectionService.getConnectionById(connectionId)
                .map(conn -> conn.getIsMock() != null && conn.getIsMock())
                .orElse(false);
    }

    /**
     * Проверить, является ли активное подключение mock-заглушкой (для обратной совместимости)
     */
    private boolean isMockConnection() {
        return openShiftClientService.getActiveConnection()
                .map(OpenShiftConnection::getIsMock)
                .orElse(false);
    }
    
    /**
     * Получить OpenShift клиент для конкретного подключения
     */
    private OpenShiftClient getClient(Long connectionId) {
        return openShiftClientService.getClientForConnection(connectionId)
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
    }

    /**
     * Получить OpenShift клиент (для обратной совместимости - использует активное подключение)
     */
    private OpenShiftClient getClient() {
        return openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
    }
    
    /**
     * Проверить, есть ли сохраненное состояние для namespace
     */
    public boolean hasState(Long connectionId, String namespace) {
        return stateService.hasState(connectionId, namespace);
    }
    
    /**
     * Проверить, есть ли сохраненное состояние (для обратной совместимости)
     * @deprecated Используйте метод с connectionId
     */
    @Deprecated
    public boolean hasState(String namespace) {
        // Используем первое активное подключение для обратной совместимости
        return openShiftClientService.getActiveConnection()
                .map(conn -> stateService.hasState(conn.getId(), namespace))
                .orElse(false);
    }

    /**
     * Получить список всех Deployments в namespace для конкретного подключения
     */
    public List<DeploymentInfo> getAllDeployments(Long connectionId, String namespace) {
        log.info("Получение списка Deployments в namespace: {} для подключения ID: {}", namespace, connectionId);
        
        // Если подключение - mock, возвращаем mock-данные
        if (isMockConnection(connectionId)) {
            log.info("Использование mock-данных для deployments в namespace: {}", namespace);
            return mockDataService.getMockDeployments(namespace);
        }
        
        OpenShiftClient openShiftClient = getClient(connectionId);
        
        try {
            DeploymentList deploymentList = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .list();
            
            List<DeploymentInfo> deployments = deploymentList.getItems().stream()
                    .map(this::mapToDeploymentInfo)
                    .collect(Collectors.toList());
            
            // Добавляем изначальное количество реплик из StateService
            deployments.forEach(deployment -> {
                Integer originalReplicas = stateService.getOriginalReplicas(
                        connectionId,
                        deployment.getNamespace(), 
                        deployment.getName()
                );
                if (originalReplicas != null) {
                    deployment.setOriginalReplicas(originalReplicas);
                } else {
                    // Если нет сохраненного состояния, используем текущее как изначальное для отображения
                    deployment.setOriginalReplicas(deployment.getCurrentReplicas());
                }
            });
            
            return deployments;
            
        } catch (KubernetesClientException e) {
            // Обработка ошибки 403 Forbidden - у пользователя нет прав на просмотр deployments
            if (e.getCode() == 403) {
                log.warn("Нет прав на получение списка deployments в namespace '{}' (403 Forbidden). " +
                        "Пользователь не имеет доступа к этому ресурсу.", namespace);
                throw new RuntimeException(
                    "Нет доступа к namespace '" + namespace + "'. " +
                    "У вашего пользователя нет прав на просмотр deployments в этом namespace. " +
                    "Обратитесь к администратору кластера для предоставления необходимых прав."
                );
            } else {
                // Для других ошибок пробрасываем исключение дальше
                log.error("Ошибка при получении списка deployments в namespace '{}': {}", namespace, e.getMessage());
                throw new RuntimeException("Ошибка при получении списка deployments: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Получить список всех Deployments в namespace (для обратной совместимости - использует активное подключение)
     */
    public List<DeploymentInfo> getAllDeployments(String namespace) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> getAllDeployments(conn.getId(), namespace))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Получить информацию о конкретном Deployment
     */
    public DeploymentInfo getDeployment(String namespace, String name) {
        log.info("Получение информации о Deployment {}/{}", namespace, name);
        
        // Если подключение - mock, возвращаем mock-данные
        if (isMockConnection()) {
            log.info("Использование mock-данных для deployment {}/{}", namespace, name);
            return mockDataService.getMockDeployment(namespace, name);
        }
        
        OpenShiftClient openShiftClient = getClient();
        
        try {
            Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (deployment == null) {
                log.warn("Deployment {}/{} не найден", namespace, name);
                return null;
            }
            
            return mapToDeploymentInfo(deployment);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 403) {
                log.warn("Нет прав на получение deployment {}/{} (403 Forbidden)", namespace, name);
                throw new RuntimeException(
                    "Нет доступа к deployment '" + name + "' в namespace '" + namespace + "'. " +
                    "У вашего пользователя нет необходимых прав."
                );
            }
            throw new RuntimeException("Ошибка при получении deployment: " + e.getMessage(), e);
        }
    }

    /**
     * Масштабировать Deployment для конкретного подключения
     */
    public boolean scaleDeployment(Long connectionId, String namespace, String name, int replicas) {
        log.info("Масштабирование Deployment {}/{} до {} реплик для подключения ID: {}", namespace, name, replicas, connectionId);
        
        // Для mock-подключений просто логируем
        if (isMockConnection(connectionId)) {
            log.info("Mock-режим: операция масштабирования deployment {}/{} до {} реплик выполнена (заглушка)", 
                    namespace, name, replicas);
            return true;
        }
        
        OpenShiftClient openShiftClient = getClient(connectionId);
        try {
            Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (deployment == null) {
                log.error("Deployment {}/{} не найден", namespace, name);
                return false;
            }
            
            // Масштабируем через scale
            openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .scale(replicas);
            
            log.info("Deployment {}/{} успешно масштабирован до {} реплик", namespace, name, replicas);
            return true;
        } catch (KubernetesClientException e) {
            if (e.getCode() == 403) {
                log.warn("Нет прав на масштабирование deployment {}/{} (403 Forbidden)", namespace, name);
                throw new RuntimeException(
                    "Нет доступа для масштабирования deployment '" + name + "' в namespace '" + namespace + "'. " +
                    "У вашего пользователя нет необходимых прав."
                );
            }
            log.error("Ошибка при масштабировании Deployment {}/{}", namespace, name, e);
            throw new RuntimeException("Ошибка при масштабировании: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Ошибка при масштабировании Deployment {}/{}", namespace, name, e);
            throw new RuntimeException("Ошибка при масштабировании: " + e.getMessage(), e);
        }
    }

    /**
     * Масштабировать Deployment (для обратной совместимости - использует активное подключение)
     */
    public boolean scaleDeployment(String namespace, String name, int replicas) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> scaleDeployment(conn.getId(), namespace, name, replicas))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Перезапустить Deployment для конкретного подключения
     */
    public boolean restartDeployment(Long connectionId, String namespace, String name) {
        log.info("Перезапуск Deployment {}/{} для подключения ID: {}", namespace, name, connectionId);
        
        // Для mock-подключений просто логируем
        if (isMockConnection(connectionId)) {
            log.info("Mock-режим: операция перезапуска deployment {}/{} выполнена (заглушка)", namespace, name);
            return true;
        }
        
        OpenShiftClient openShiftClient = getClient(connectionId);
        try {
            // Перезапуск через rollout restart
            openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .rolling()
                    .restart();
            
            log.info("Deployment {}/{} успешно перезапущен", namespace, name);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при перезапуске Deployment {}/{}", namespace, name, e);
            // Если rollout restart не поддерживается, удаляем поды
            try {
                return restartByDeletingPods(connectionId, namespace, name);
            } catch (Exception ex) {
                throw new RuntimeException("Ошибка при перезапуске: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Перезапустить Deployment (для обратной совместимости - использует активное подключение)
     */
    public boolean restartDeployment(String namespace, String name) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restartDeployment(conn.getId(), namespace, name))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Перезапустить все Deployments в namespace для конкретного подключения
     */
    public Map<String, Boolean> restartAllDeployments(Long connectionId, String namespace) {
        log.info("Перезапуск всех Deployments в namespace: {} для подключения ID: {}", namespace, connectionId);
        List<DeploymentInfo> deployments = getAllDeployments(connectionId, namespace);
        return deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        d -> {
                            try {
                                return restartDeployment(connectionId, namespace, d.getName());
                            } catch (Exception e) {
                                log.error("Ошибка при перезапуске deployment {} в namespace {}", d.getName(), namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Перезапустить все Deployments в namespace (для обратной совместимости - использует активное подключение)
     */
    public Map<String, Boolean> restartAllDeployments(String namespace) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restartAllDeployments(conn.getId(), namespace))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Установить все Deployments в 0 реплик (остановить все) для конкретного подключения
     */
    public Map<String, Boolean> shutdownAllDeployments(Long connectionId, String namespace) {
        log.info("Остановка всех Deployments в namespace: {} для подключения ID: {}", namespace, connectionId);
        
        // Сохраняем текущее состояние перед остановкой
        saveCurrentState(connectionId, namespace);
        
        List<DeploymentInfo> deployments = getAllDeployments(connectionId, namespace);
        return deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        d -> {
                            try {
                                return scaleDeployment(connectionId, namespace, d.getName(), 0);
                            } catch (Exception e) {
                                log.error("Ошибка при остановке deployment {} в namespace {}", d.getName(), namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Установить все Deployments в 0 реплик (остановить все) (для обратной совместимости - использует активное подключение)
     */
    public Map<String, Boolean> shutdownAllDeployments(String namespace) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> shutdownAllDeployments(conn.getId(), namespace))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Восстановить изначальное состояние всех Deployments для конкретного подключения
     */
    public Map<String, Boolean> restoreAllDeployments(Long connectionId, String namespace) {
        log.info("Восстановление изначального состояния всех Deployments в namespace: {} для подключения ID: {}", namespace, connectionId);
        
        Map<String, Integer> originalState = stateService.getState(connectionId, namespace);
        if (originalState.isEmpty()) {
            log.warn("Нет сохраненного состояния для namespace: {}", namespace);
            return Map.of();
        }
        
        return originalState.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            try {
                                return scaleDeployment(connectionId, namespace, entry.getKey(), entry.getValue());
                            } catch (Exception e) {
                                log.error("Ошибка при восстановлении deployment {} в namespace {}", entry.getKey(), namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Восстановить изначальное состояние всех Deployments (для обратной совместимости - использует активное подключение)
     */
    public Map<String, Boolean> restoreAllDeployments(String namespace) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restoreAllDeployments(conn.getId(), namespace))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Сохранить текущее состояние всех Deployments для конкретного подключения
     */
    public void saveCurrentState(Long connectionId, String namespace) {
        log.info("Сохранение текущего состояния Deployments в namespace: {} для подключения ID: {}", namespace, connectionId);
        
        List<DeploymentInfo> deployments = getAllDeployments(connectionId, namespace);
        Map<String, Integer> state = deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        DeploymentInfo::getCurrentReplicas
                ));
        
        stateService.saveState(connectionId, namespace, state);
    }

    /**
     * Сохранить текущее состояние всех Deployments (для обратной совместимости - использует активное подключение)
     */
    public void saveCurrentState(String namespace) {
        openShiftClientService.getActiveConnection()
                .ifPresent(conn -> saveCurrentState(conn.getId(), namespace));
    }

    /**
     * Перезапустить Deployment через удаление подов для конкретного подключения
     */
    private boolean restartByDeletingPods(Long connectionId, String namespace, String name) {
        log.info("Перезапуск Deployment {}/{} через удаление подов для подключения ID: {}", namespace, name, connectionId);
        
        OpenShiftClient openShiftClient = getClient(connectionId);
        try {
            // Находим все поды этого Deployment через selector
            Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (deployment == null) {
                log.error("Deployment {}/{} не найден", namespace, name);
                return false;
            }
            
            // Используем labels из Deployment для поиска подов
            Map<String, String> labels = deployment.getSpec().getSelector().getMatchLabels();
            if (labels == null || labels.isEmpty()) {
                // Fallback на стандартный label
                labels = Map.of("app", name);
            }
            
            openShiftClient.pods()
                    .inNamespace(namespace)
                    .withLabels(labels)
                    .delete();
            
            log.info("Поды Deployment {}/{} удалены, будут пересозданы", namespace, name);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении подов Deployment {}/{}", namespace, name, e);
            return false;
        }
    }

    /**
     * Перезапустить Deployment через удаление подов (для обратной совместимости - использует активное подключение)
     */
    private boolean restartByDeletingPods(String namespace, String name) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restartByDeletingPods(conn.getId(), namespace, name))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Перезапустить все поды для конкретного Deployment (с connectionId)
     */
    public boolean restartAllPodsForDeployment(Long connectionId, String namespace, String deploymentName) {
        log.info("Перезапуск всех подов для Deployment {}/{} для подключения ID: {}", namespace, deploymentName, connectionId);
        return restartByDeletingPods(connectionId, namespace, deploymentName);
    }

    /**
     * Перезапустить все поды для конкретного Deployment (для обратной совместимости - использует активное подключение)
     */
    public boolean restartAllPodsForDeployment(String namespace, String deploymentName) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restartAllPodsForDeployment(conn.getId(), namespace, deploymentName))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Остановить Deployment (установить количество реплик в 0) для конкретного подключения
     * Сохраняет текущее состояние перед остановкой
     */
    public boolean deleteAllPodsForDeployment(Long connectionId, String namespace, String deploymentName) {
        log.info("Остановка Deployment {}/{} (установка реплик в 0) для подключения ID: {}", namespace, deploymentName, connectionId);
        
        try {
            // Сохраняем текущее состояние перед остановкой (если еще не сохранено)
            Integer currentReplicas = null;
            try {
                List<DeploymentInfo> deployments = getAllDeployments(connectionId, namespace);
                DeploymentInfo deploymentInfo = deployments.stream()
                        .filter(d -> d.getName().equals(deploymentName))
                        .findFirst()
                        .orElse(null);
                if (deploymentInfo != null) {
                    currentReplicas = deploymentInfo.getCurrentReplicas();
                }
            } catch (Exception e) {
                log.warn("Не удалось получить текущее состояние Deployment {}/{}", namespace, deploymentName, e);
            }
            
            // Если нет сохраненного состояния, сохраняем текущее
            if (currentReplicas != null && stateService.getOriginalReplicas(connectionId, namespace, deploymentName) == null) {
                stateService.saveDeploymentState(connectionId, namespace, deploymentName, currentReplicas);
            }
            
            // Устанавливаем количество реплик в 0
            boolean success = scaleDeployment(connectionId, namespace, deploymentName, 0);
            
            if (success) {
                log.info("Deployment {}/{} остановлен (реплики установлены в 0)", namespace, deploymentName);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Ошибка при остановке Deployment {}/{}", namespace, deploymentName, e);
            return false;
        }
    }

    /**
     * Остановить Deployment (установить количество реплик в 0) (для обратной совместимости - использует активное подключение)
     * Сохраняет текущее состояние перед остановкой
     */
    public boolean deleteAllPodsForDeployment(String namespace, String deploymentName) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> deleteAllPodsForDeployment(conn.getId(), namespace, deploymentName))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Восстановить поды для конкретного Deployment (вернуть количество реплик к исходному) для конкретного подключения
     */
    public boolean restorePodsForDeployment(Long connectionId, String namespace, String deploymentName) {
        log.info("Восстановление подов для Deployment {}/{} для подключения ID: {}", namespace, deploymentName, connectionId);
        
        try {
            Integer originalReplicas = stateService.getOriginalReplicas(connectionId, namespace, deploymentName);
            if (originalReplicas == null) {
                log.warn("Нет сохраненного состояния для Deployment {}/{}", namespace, deploymentName);
                return false;
            }
            
            return scaleDeployment(connectionId, namespace, deploymentName, originalReplicas);
        } catch (Exception e) {
            log.error("Ошибка при восстановлении подов Deployment {}/{}", namespace, deploymentName, e);
            return false;
        }
    }

    /**
     * Восстановить поды для конкретного Deployment (вернуть количество реплик к исходному) (для обратной совместимости - использует активное подключение)
     */
    public boolean restorePodsForDeployment(String namespace, String deploymentName) {
        return openShiftClientService.getActiveConnection()
                .map(conn -> restorePodsForDeployment(conn.getId(), namespace, deploymentName))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }

    /**
     * Маппинг Deployment в DeploymentInfo DTO
     */
    private DeploymentInfo mapToDeploymentInfo(Deployment deployment) {
        Integer replicas = deployment.getSpec().getReplicas() != null 
                ? deployment.getSpec().getReplicas() 
                : 0;
        
        Integer availableReplicas = deployment.getStatus().getAvailableReplicas() != null
                ? deployment.getStatus().getAvailableReplicas()
                : 0;
        
        Integer readyReplicas = deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas()
                : 0;
        
        return DeploymentInfo.builder()
                .name(deployment.getMetadata().getName())
                .namespace(deployment.getMetadata().getNamespace())
                .currentReplicas(replicas)
                .availableReplicas(availableReplicas)
                .readyReplicas(readyReplicas)
                .creationTimestamp(deployment.getMetadata().getCreationTimestamp() != null
                        ? parseCreationTimestamp(deployment.getMetadata().getCreationTimestamp())
                        : null)
                .labels(deployment.getMetadata().getLabels())
                .status(deployment.getStatus().getConditions() != null && !deployment.getStatus().getConditions().isEmpty()
                        ? deployment.getStatus().getConditions().get(0).getType()
                        : "Unknown")
                .build();
    }

    /**
     * Парсинг строки с timestamp в Instant
     */
    private Instant parseCreationTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            log.warn("Не удалось распарсить timestamp: {}", timestamp, e);
            return null;
        }
    }

    /**
     * Массовое масштабирование выбранных Deployments
     */
    public Map<String, Boolean> scaleSelectedDeployments(Long connectionId, String namespace, List<String> deploymentNames, int replicas) {
        log.info("Массовое масштабирование {} deployments в namespace: {} для подключения ID: {} до {} реплик", 
                deploymentNames.size(), namespace, connectionId, replicas);
        return deploymentNames.stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            try {
                                return scaleDeployment(connectionId, namespace, name, replicas);
                            } catch (Exception e) {
                                log.error("Ошибка при масштабировании deployment {} в namespace {}", name, namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Массовый перезапуск выбранных Deployments
     */
    public Map<String, Boolean> restartSelectedDeployments(Long connectionId, String namespace, List<String> deploymentNames) {
        log.info("Массовый перезапуск {} deployments в namespace: {} для подключения ID: {}", 
                deploymentNames.size(), namespace, connectionId);
        return deploymentNames.stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            try {
                                return restartDeployment(connectionId, namespace, name);
                            } catch (Exception e) {
                                log.error("Ошибка при перезапуске deployment {} в namespace {}", name, namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Массовая остановка выбранных Deployments (установка в 0)
     */
    public Map<String, Boolean> shutdownSelectedDeployments(Long connectionId, String namespace, List<String> deploymentNames) {
        log.info("Массовая остановка {} deployments в namespace: {} для подключения ID: {}", 
                deploymentNames.size(), namespace, connectionId);
        // Сохраняем состояние перед остановкой
        saveCurrentState(connectionId, namespace);
        return deploymentNames.stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            try {
                                return scaleDeployment(connectionId, namespace, name, 0);
                            } catch (Exception e) {
                                log.error("Ошибка при остановке deployment {} в namespace {}", name, namespace, e);
                                return false;
                            }
                        }
                ));
    }

    /**
     * Массовое восстановление выбранных Deployments
     */
    public Map<String, Boolean> restoreSelectedDeployments(Long connectionId, String namespace, List<String> deploymentNames) {
        log.info("Массовое восстановление {} deployments в namespace: {} для подключения ID: {}", 
                deploymentNames.size(), namespace, connectionId);
        
        Map<String, Integer> originalState = stateService.getState(connectionId, namespace);
        if (originalState.isEmpty()) {
            log.warn("Нет сохраненного состояния для namespace: {}", namespace);
            return Map.of();
        }
        
        return deploymentNames.stream()
                .filter(originalState::containsKey)
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            try {
                                return scaleDeployment(connectionId, namespace, name, originalState.get(name));
                            } catch (Exception e) {
                                log.error("Ошибка при восстановлении deployment {} в namespace {}", name, namespace, e);
                                return false;
                            }
                        }
                ));
    }
}

