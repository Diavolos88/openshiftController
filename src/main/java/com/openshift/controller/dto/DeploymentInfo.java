package com.openshift.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO для информации о Deployment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfo {
    private String name;
    private String namespace;
    private Integer currentReplicas;      // Текущее желаемое количество реплик
    private Integer availableReplicas;     // Доступные поды
    private Integer readyReplicas;         // Готовые поды
    private Integer originalReplicas;      // Изначальное количество (сохраненное)
    private Map<String, String> labels;
    private Instant creationTimestamp;
    private String status;
}

