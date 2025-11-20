package com.openshift.controller.service;

import com.openshift.controller.dto.DeploymentInfo;
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
    private final StateService stateService;
    
    /**
     * Получить OpenShift клиент
     */
    private OpenShiftClient getClient() {
        return openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
    }
    
    /**
     * Проверить, есть ли сохраненное состояние для namespace
     */
    public boolean hasState(String namespace) {
        return stateService.hasState(namespace);
    }

    /**
     * Получить список всех Deployments в namespace
     */
    public List<DeploymentInfo> getAllDeployments(String namespace) {
        log.info("Получение списка Deployments в namespace: {}", namespace);
        
        OpenShiftClient openShiftClient = getClient();
        
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
                        deployment.getNamespace(), 
                        deployment.getName()
                );
                if (originalReplicas != null) {
                    deployment.setOriginalReplicas(originalReplicas);
                } else {
                    // Если нет сохраненного состояния, используем текущее как изначальное
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
     * Получить информацию о конкретном Deployment
     */
    public DeploymentInfo getDeployment(String namespace, String name) {
        log.info("Получение информации о Deployment {}/{}", namespace, name);
        
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
     * Масштабировать Deployment
     */
    public boolean scaleDeployment(String namespace, String name, int replicas) {
        log.info("Масштабирование Deployment {}/{} до {} реплик", namespace, name, replicas);
        
        OpenShiftClient openShiftClient = getClient();
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
     * Перезапустить Deployment
     */
    public boolean restartDeployment(String namespace, String name) {
        log.info("Перезапуск Deployment {}/{}", namespace, name);
        
        OpenShiftClient openShiftClient = getClient();
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
                return restartByDeletingPods(namespace, name);
            } catch (Exception ex) {
                throw new RuntimeException("Ошибка при перезапуске: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Перезапустить все Deployments в namespace
     */
    public Map<String, Boolean> restartAllDeployments(String namespace) {
        log.info("Перезапуск всех Deployments в namespace: {}", namespace);
        List<DeploymentInfo> deployments = getAllDeployments(namespace);
        return deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        d -> restartDeployment(namespace, d.getName())
                ));
    }

    /**
     * Установить все Deployments в 0 реплик (остановить все)
     */
    public Map<String, Boolean> shutdownAllDeployments(String namespace) {
        log.info("Остановка всех Deployments в namespace: {}", namespace);
        
        // Сохраняем текущее состояние перед остановкой
        saveCurrentState(namespace);
        
        List<DeploymentInfo> deployments = getAllDeployments(namespace);
        return deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        d -> scaleDeployment(namespace, d.getName(), 0)
                ));
    }

    /**
     * Восстановить изначальное состояние всех Deployments
     */
    public Map<String, Boolean> restoreAllDeployments(String namespace) {
        log.info("Восстановление изначального состояния всех Deployments в namespace: {}", namespace);
        
        Map<String, Integer> originalState = stateService.getState(namespace);
        if (originalState.isEmpty()) {
            log.warn("Нет сохраненного состояния для namespace: {}", namespace);
            return Map.of();
        }
        
        return originalState.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> scaleDeployment(namespace, entry.getKey(), entry.getValue())
                ));
    }

    /**
     * Сохранить текущее состояние всех Deployments
     */
    public void saveCurrentState(String namespace) {
        log.info("Сохранение текущего состояния Deployments в namespace: {}", namespace);
        
        List<DeploymentInfo> deployments = getAllDeployments(namespace);
        Map<String, Integer> state = deployments.stream()
                .collect(Collectors.toMap(
                        DeploymentInfo::getName,
                        DeploymentInfo::getCurrentReplicas
                ));
        
        stateService.saveState(namespace, state);
    }

    /**
     * Перезапустить Deployment через удаление подов
     */
    private boolean restartByDeletingPods(String namespace, String name) {
        log.info("Перезапуск Deployment {}/{} через удаление подов", namespace, name);
        
        OpenShiftClient openShiftClient = getClient();
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
     * Перезапустить все поды для конкретного Deployment
     */
    public boolean restartAllPodsForDeployment(String namespace, String deploymentName) {
        log.info("Перезапуск всех подов для Deployment {}/{}", namespace, deploymentName);
        return restartByDeletingPods(namespace, deploymentName);
    }

    /**
     * Остановить Deployment (установить количество реплик в 0)
     * Сохраняет текущее состояние перед остановкой
     */
    public boolean deleteAllPodsForDeployment(String namespace, String deploymentName) {
        log.info("Остановка Deployment {}/{} (установка реплик в 0)", namespace, deploymentName);
        
        try {
            // Сохраняем текущее состояние перед остановкой (если еще не сохранено)
            Integer currentReplicas = null;
            try {
                DeploymentInfo deploymentInfo = getDeployment(namespace, deploymentName);
                if (deploymentInfo != null) {
                    currentReplicas = deploymentInfo.getCurrentReplicas();
                }
            } catch (Exception e) {
                log.warn("Не удалось получить текущее состояние Deployment {}/{}", namespace, deploymentName, e);
            }
            
            // Если нет сохраненного состояния, сохраняем текущее
            if (currentReplicas != null && stateService.getOriginalReplicas(namespace, deploymentName) == null) {
                stateService.saveDeploymentState(namespace, deploymentName, currentReplicas);
            }
            
            // Устанавливаем количество реплик в 0
            boolean success = scaleDeployment(namespace, deploymentName, 0);
            
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
     * Восстановить поды для конкретного Deployment (вернуть количество реплик к исходному)
     */
    public boolean restorePodsForDeployment(String namespace, String deploymentName) {
        log.info("Восстановление подов для Deployment {}/{}", namespace, deploymentName);
        
        try {
            Integer originalReplicas = stateService.getOriginalReplicas(namespace, deploymentName);
            if (originalReplicas == null) {
                log.warn("Нет сохраненного состояния для Deployment {}/{}", namespace, deploymentName);
                return false;
            }
            
            return scaleDeployment(namespace, deploymentName, originalReplicas);
        } catch (Exception e) {
            log.error("Ошибка при восстановлении подов Deployment {}/{}", namespace, deploymentName, e);
            return false;
        }
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
}

