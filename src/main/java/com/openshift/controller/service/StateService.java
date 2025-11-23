package com.openshift.controller.service;

import com.openshift.controller.entity.DeploymentState;
import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.repository.DeploymentStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для сохранения и восстановления изначального состояния Deployments
 * Хранит стартовые значения в БД
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateService {

    private final DeploymentStateRepository repository;

    /**
     * Сохранить текущее состояние всех Deployments в namespace для подключения
     */
    @Transactional
    public void saveState(Long connectionId, String namespace, Map<String, Integer> deploymentsState) {
        log.info("Сохранение состояния для подключения ID: {}, namespace: {}", connectionId, namespace);
        
        // Удаляем старые состояния для этого подключения и namespace
        repository.deleteByConnectionIdAndNamespace(connectionId, namespace);
        
        // Сохраняем новые состояния
        for (Map.Entry<String, Integer> entry : deploymentsState.entrySet()) {
            DeploymentState state = DeploymentState.builder()
                    .connection(OpenShiftConnection.builder().id(connectionId).build())
                    .namespace(namespace)
                    .deploymentName(entry.getKey())
                    .originalReplicas(entry.getValue())
                    .build();
            repository.save(state);
        }
        
        log.info("Сохранено {} Deployment'ов для подключения ID: {}, namespace: {}", 
                deploymentsState.size(), connectionId, namespace);
    }

    /**
     * Сохранить состояние одного Deployment
     */
    @Transactional
    public void saveDeploymentState(Long connectionId, String namespace, String deploymentName, Integer replicas) {
        DeploymentState existing = repository
                .findByConnectionIdAndNamespaceAndDeploymentName(connectionId, namespace, deploymentName)
                .orElse(null);
        
        if (existing != null) {
            existing.setOriginalReplicas(replicas);
            repository.save(existing);
            log.info("Обновлено состояние для Deployment {}/{}/{}: {} реплик", 
                    connectionId, namespace, deploymentName, replicas);
        } else {
            DeploymentState state = DeploymentState.builder()
                    .connection(OpenShiftConnection.builder().id(connectionId).build())
                    .namespace(namespace)
                    .deploymentName(deploymentName)
                    .originalReplicas(replicas)
                    .build();
            repository.save(state);
            log.info("Сохранено состояние для Deployment {}/{}/{}: {} реплик", 
                    connectionId, namespace, deploymentName, replicas);
        }
    }

    /**
     * Получить сохраненное состояние для namespace подключения
     */
    public Map<String, Integer> getState(Long connectionId, String namespace) {
        List<DeploymentState> states = repository.findByConnectionIdAndNamespace(connectionId, namespace);
        return states.stream()
                .collect(Collectors.toMap(
                        DeploymentState::getDeploymentName,
                        DeploymentState::getOriginalReplicas
                ));
    }

    /**
     * Получить изначальное количество реплик для конкретного Deployment
     */
    public Integer getOriginalReplicas(Long connectionId, String namespace, String deploymentName) {
        return repository
                .findByConnectionIdAndNamespaceAndDeploymentName(connectionId, namespace, deploymentName)
                .map(DeploymentState::getOriginalReplicas)
                .orElse(null);
    }

    /**
     * Проверить, есть ли сохраненное состояние для namespace подключения
     */
    public boolean hasState(Long connectionId, String namespace) {
        return repository.existsByConnectionIdAndNamespace(connectionId, namespace);
    }

    /**
     * Очистить сохраненное состояние для namespace подключения
     */
    @Transactional
    public void clearState(Long connectionId, String namespace) {
        log.info("Очистка состояния для подключения ID: {}, namespace: {}", connectionId, namespace);
        repository.deleteByConnectionIdAndNamespace(connectionId, namespace);
    }

    /**
     * Методы для обратной совместимости (без connectionId) - использовать первое активное подключение
     * @deprecated Используйте методы с connectionId
     */
    @Deprecated
    public void saveState(String namespace, Map<String, Integer> deploymentsState) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод saveState без connectionId для namespace: {}", namespace);
    }

    @Deprecated
    public Map<String, Integer> getState(String namespace) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод getState без connectionId для namespace: {}", namespace);
        return new HashMap<>();
    }

    @Deprecated
    public Integer getOriginalReplicas(String namespace, String deploymentName) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод getOriginalReplicas без connectionId для namespace: {}", namespace);
        return null;
    }

    @Deprecated
    public boolean hasState(String namespace) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод hasState без connectionId для namespace: {}", namespace);
        return false;
    }

    @Deprecated
    public void saveDeploymentState(String namespace, String deploymentName, Integer replicas) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод saveDeploymentState без connectionId для namespace: {}", namespace);
    }

    @Deprecated
    public void clearState(String namespace) {
        // Метод для обратной совместимости - не использовать
        log.warn("Использован устаревший метод clearState без connectionId для namespace: {}", namespace);
    }
}
