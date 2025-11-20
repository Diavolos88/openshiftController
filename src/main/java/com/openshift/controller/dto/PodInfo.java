package com.openshift.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO для информации о поде
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {
    private String name;
    private String namespace;
    private String status;
    private String nodeName;
    private Instant creationTimestamp;
    private Map<String, String> labels;
    private Map<String, String> annotations;
}

