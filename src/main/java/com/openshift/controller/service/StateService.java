package com.openshift.controller.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для сохранения и восстановления изначального состояния Deployments
 */
@Slf4j
@Service
public class StateService {

    // Хранение изначальных состояний: namespace -> (deploymentName -> originalReplicas)
    private final ConcurrentHashMap<String, Map<String, Integer>> stateStorage = new ConcurrentHashMap<>();

    /**
     * Сохранить текущее состояние всех Deployments в namespace
     */
    public void saveState(String namespace, Map<String, Integer> deploymentsState) {
        log.info("Сохранение состояния для namespace: {}", namespace);
        stateStorage.put(namespace, new ConcurrentHashMap<>(deploymentsState));
        log.info("Сохранено {} Deployment'ов для namespace {}", deploymentsState.size(), namespace);
    }

    /**
     * Сохранить состояние одного Deployment
     */
    public void saveDeploymentState(String namespace, String deploymentName, Integer replicas) {
        Map<String, Integer> namespaceState = stateStorage.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>());
        namespaceState.put(deploymentName, replicas);
        log.info("Сохранено состояние для Deployment {}/{}: {} реплик", namespace, deploymentName, replicas);
    }

    /**
     * Получить сохраненное состояние для namespace
     */
    public Map<String, Integer> getState(String namespace) {
        return stateStorage.getOrDefault(namespace, new ConcurrentHashMap<>());
    }

    /**
     * Получить изначальное количество реплик для конкретного Deployment
     */
    public Integer getOriginalReplicas(String namespace, String deploymentName) {
        Map<String, Integer> namespaceState = stateStorage.get(namespace);
        if (namespaceState == null) {
            return null;
        }
        return namespaceState.get(deploymentName);
    }

    /**
     * Проверить, есть ли сохраненное состояние для namespace
     */
    public boolean hasState(String namespace) {
        return stateStorage.containsKey(namespace) && !stateStorage.get(namespace).isEmpty();
    }

    /**
     * Очистить сохраненное состояние для namespace
     */
    public void clearState(String namespace) {
        log.info("Очистка состояния для namespace: {}", namespace);
        stateStorage.remove(namespace);
    }
}

