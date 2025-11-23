package com.openshift.controller.service;

import com.openshift.controller.dto.DeploymentInfo;
import com.openshift.controller.dto.PodInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Сервис для предоставления mock-данных для тестирования
 * Используется когда подключение помечено как isMock = true
 */
@Slf4j
@Service
public class MockDataService {

    /**
     * Получить список тестовых namespaces
     */
    public List<String> getMockNamespaces() {
        return Arrays.asList(
            "test-project",
            "staging",
            "production",
            "development"
        );
    }

    /**
     * Получить список тестовых подов для указанного namespace
     */
    public List<PodInfo> getMockPods(String namespace) {
        log.debug("Генерация mock-подов для namespace: {}", namespace);
        
        List<PodInfo> pods = new ArrayList<>();
        
        // Генерируем разные поды в зависимости от namespace
        switch (namespace.toLowerCase()) {
            case "test-project":
                pods.add(createMockPod("web-app-1", namespace, "Running", "node-01", 
                    Map.of("app", "web-app", "version", "1.0"), Instant.now().minus(2, ChronoUnit.HOURS)));
                pods.add(createMockPod("web-app-2", namespace, "Running", "node-02",
                    Map.of("app", "web-app", "version", "1.0"), Instant.now().minus(1, ChronoUnit.HOURS)));
                pods.add(createMockPod("api-service-1", namespace, "Running", "node-01",
                    Map.of("app", "api", "version", "2.1"), Instant.now().minus(3, ChronoUnit.HOURS)));
                pods.add(createMockPod("db-pod", namespace, "Running", "node-03",
                    Map.of("app", "database", "tier", "backend"), Instant.now().minus(5, ChronoUnit.DAYS)));
                break;
                
            case "staging":
                pods.add(createMockPod("staging-web-1", namespace, "Running", "node-02",
                    Map.of("app", "web", "environment", "staging"), Instant.now().minus(1, ChronoUnit.DAYS)));
                pods.add(createMockPod("staging-api-1", namespace, "Running", "node-01",
                    Map.of("app", "api", "environment", "staging"), Instant.now().minus(1, ChronoUnit.DAYS)));
                pods.add(createMockPod("staging-worker", namespace, "Pending", "node-03",
                    Map.of("app", "worker", "environment", "staging"), Instant.now().minus(30, ChronoUnit.MINUTES)));
                break;
                
            case "production":
                pods.add(createMockPod("prod-web-1", namespace, "Running", "node-01",
                    Map.of("app", "web", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                pods.add(createMockPod("prod-web-2", namespace, "Running", "node-02",
                    Map.of("app", "web", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                pods.add(createMockPod("prod-web-3", namespace, "Running", "node-03",
                    Map.of("app", "web", "environment", "prod"), Instant.now().minus(9, ChronoUnit.DAYS)));
                pods.add(createMockPod("prod-api-1", namespace, "Running", "node-01",
                    Map.of("app", "api", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                pods.add(createMockPod("prod-api-2", namespace, "Running", "node-02",
                    Map.of("app", "api", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                pods.add(createMockPod("prod-db-primary", namespace, "Running", "node-03",
                    Map.of("app", "database", "role", "primary", "environment", "prod"), Instant.now().minus(30, ChronoUnit.DAYS)));
                break;
                
            case "development":
                pods.add(createMockPod("dev-app-1", namespace, "Running", "node-02",
                    Map.of("app", "dev-app", "env", "dev"), Instant.now().minus(2, ChronoUnit.HOURS)));
                pods.add(createMockPod("dev-app-2", namespace, "CrashLoopBackOff", "node-01",
                    Map.of("app", "dev-app", "env", "dev"), Instant.now().minus(1, ChronoUnit.HOURS)));
                break;
                
            default:
                // Для неизвестных namespace создаем несколько базовых подов
                pods.add(createMockPod("app-pod-1", namespace, "Running", "node-01",
                    Map.of("app", "application"), Instant.now().minus(1, ChronoUnit.DAYS)));
                pods.add(createMockPod("app-pod-2", namespace, "Running", "node-02",
                    Map.of("app", "application"), Instant.now().minus(1, ChronoUnit.DAYS)));
        }
        
        return pods;
    }

    /**
     * Получить конкретный mock-под
     */
    public PodInfo getMockPod(String namespace, String podName) {
        List<PodInfo> pods = getMockPods(namespace);
        return pods.stream()
                .filter(p -> p.getName().equals(podName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Получить список тестовых deployments для указанного namespace
     */
    public List<DeploymentInfo> getMockDeployments(String namespace) {
        log.debug("Генерация mock-deployments для namespace: {}", namespace);
        
        List<DeploymentInfo> deployments = new ArrayList<>();
        
        switch (namespace.toLowerCase()) {
            case "test-project":
                deployments.add(createMockDeployment("web-app", namespace, 2, 2, 2, 
                    Map.of("app", "web-app"), Instant.now().minus(2, ChronoUnit.HOURS)));
                deployments.add(createMockDeployment("api-service", namespace, 1, 1, 1,
                    Map.of("app", "api"), Instant.now().minus(3, ChronoUnit.HOURS)));
                deployments.add(createMockDeployment("database", namespace, 1, 1, 1,
                    Map.of("app", "database"), Instant.now().minus(5, ChronoUnit.DAYS)));
                break;
                
            case "staging":
                deployments.add(createMockDeployment("staging-web", namespace, 2, 1, 1,
                    Map.of("app", "web", "environment", "staging"), Instant.now().minus(1, ChronoUnit.DAYS)));
                deployments.add(createMockDeployment("staging-api", namespace, 1, 1, 1,
                    Map.of("app", "api", "environment", "staging"), Instant.now().minus(1, ChronoUnit.DAYS)));
                deployments.add(createMockDeployment("staging-worker", namespace, 1, 0, 0,
                    Map.of("app", "worker", "environment", "staging"), Instant.now().minus(30, ChronoUnit.MINUTES)));
                break;
                
            case "production":
                deployments.add(createMockDeployment("prod-web", namespace, 3, 3, 3,
                    Map.of("app", "web", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                deployments.add(createMockDeployment("prod-api", namespace, 2, 2, 2,
                    Map.of("app", "api", "environment", "prod"), Instant.now().minus(10, ChronoUnit.DAYS)));
                deployments.add(createMockDeployment("prod-database", namespace, 1, 1, 1,
                    Map.of("app", "database", "environment", "prod"), Instant.now().minus(30, ChronoUnit.DAYS)));
                break;
                
            case "development":
                deployments.add(createMockDeployment("dev-app", namespace, 2, 1, 1,
                    Map.of("app", "dev-app", "env", "dev"), Instant.now().minus(2, ChronoUnit.HOURS)));
                break;
                
            default:
                deployments.add(createMockDeployment("application", namespace, 2, 2, 2,
                    Map.of("app", "application"), Instant.now().minus(1, ChronoUnit.DAYS)));
        }
        
        return deployments;
    }

    /**
     * Получить конкретный mock-deployment
     */
    public DeploymentInfo getMockDeployment(String namespace, String deploymentName) {
        List<DeploymentInfo> deployments = getMockDeployments(namespace);
        return deployments.stream()
                .filter(d -> d.getName().equals(deploymentName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Создать mock-под
     */
    private PodInfo createMockPod(String name, String namespace, String status, 
                                  String nodeName, Map<String, String> labels, Instant creationTime) {
        return PodInfo.builder()
                .name(name)
                .namespace(namespace)
                .status(status)
                .nodeName(nodeName)
                .creationTimestamp(creationTime)
                .labels(labels)
                .annotations(Map.of("mock", "true", "description", "Тестовый под для визуального тестирования"))
                .build();
    }

    /**
     * Создать mock-deployment
     */
    private DeploymentInfo createMockDeployment(String name, String namespace, 
                                                int currentReplicas, int availableReplicas, int readyReplicas,
                                                Map<String, String> labels, Instant creationTime) {
        return DeploymentInfo.builder()
                .name(name)
                .namespace(namespace)
                .currentReplicas(currentReplicas)
                .availableReplicas(availableReplicas)
                .readyReplicas(readyReplicas)
                .originalReplicas(currentReplicas)
                .labels(labels)
                .creationTimestamp(creationTime)
                .status(availableReplicas == currentReplicas ? "Available" : "Progressing")
                .build();
    }
}
