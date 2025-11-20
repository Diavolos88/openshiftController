package com.openshift.controller.controller;

import com.openshift.controller.dto.ApiResponse;
import com.openshift.controller.dto.PodInfo;
import com.openshift.controller.service.PodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * REST контроллер для управления подами в OpenShift
 * 
 * Endpoints:
 * - GET /api/pods/{namespace} - получить список всех подов
 * - GET /api/pods/{namespace}/{podName} - получить информацию о поде
 * - POST /api/pods/{namespace}/{podName}/restart - перезапустить под
 * - DELETE /api/pods/{namespace}/{podName} - удалить под
 * - GET /api/pods/{namespace}/search?labelSelector=... - найти поды по label selector
 */
@Slf4j
@RestController
@RequestMapping("/api/pods")
@RequiredArgsConstructor
public class PodController {

    private final PodService podService;

    @Value("${openshift.namespace:default}")
    private String defaultNamespace;

    /**
     * Получить список всех подов в namespace
     * 
     * @param namespace namespace для поиска (опционально, используется default из конфига)
     */
    @GetMapping("/{namespace}")
    public ResponseEntity<ApiResponse<List<PodInfo>>> getAllPods(
            @PathVariable(required = false) String namespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            List<PodInfo> pods = podService.getAllPods(ns);
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Найдено %d подов в namespace %s", pods.size(), ns), 
                    pods));
        } catch (Exception e) {
            log.error("Ошибка при получении списка подов", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при получении списка подов: " + e.getMessage()));
        }
    }

    /**
     * Получить информацию о конкретном поде
     */
    @GetMapping("/{namespace}/{podName}")
    public ResponseEntity<ApiResponse<PodInfo>> getPod(
            @PathVariable @NotBlank String namespace,
            @PathVariable @NotBlank String podName) {
        try {
            PodInfo pod = podService.getPod(namespace, podName);
            if (pod == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(
                                String.format("Под %s/%s не найден", namespace, podName)));
            }
            return ResponseEntity.ok(ApiResponse.success("Под найден", pod));
        } catch (Exception e) {
            log.error("Ошибка при получении информации о поде", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при получении информации о поде: " + e.getMessage()));
        }
    }

    /**
     * Перезапустить под
     * 
     * Удаляет под, и если он управляется через Deployment, он будет автоматически пересоздан
     */
    @PostMapping("/{namespace}/{podName}/restart")
    public ResponseEntity<ApiResponse<Void>> restartPod(
            @PathVariable @NotBlank String namespace,
            @PathVariable @NotBlank String podName) {
        try {
            boolean success = podService.restartPod(namespace, podName);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(
                        String.format("Под %s/%s успешно перезапущен", namespace, podName), 
                        null));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(
                                String.format("Не удалось перезапустить под %s/%s", namespace, podName)));
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске пода", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при перезапуске пода: " + e.getMessage()));
        }
    }

    /**
     * Удалить под полностью
     */
    @DeleteMapping("/{namespace}/{podName}")
    public ResponseEntity<ApiResponse<Void>> deletePod(
            @PathVariable @NotBlank String namespace,
            @PathVariable @NotBlank String podName) {
        try {
            boolean success = podService.deletePod(namespace, podName);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(
                        String.format("Под %s/%s успешно удален", namespace, podName), 
                        null));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(
                                String.format("Под %s/%s не найден", namespace, podName)));
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении пода", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при удалении пода: " + e.getMessage()));
        }
    }

    /**
     * Найти поды по label selector
     * 
     * Пример: /api/pods/my-namespace/search?labelSelector=app=myapp
     */
    @GetMapping("/{namespace}/search")
    public ResponseEntity<ApiResponse<List<PodInfo>>> searchPodsByLabel(
            @PathVariable @NotBlank String namespace,
            @RequestParam @NotBlank String labelSelector) {
        try {
            List<PodInfo> pods = podService.getPodsByLabel(namespace, labelSelector);
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Найдено %d подов с селектором %s", pods.size(), labelSelector), 
                    pods));
        } catch (Exception e) {
            log.error("Ошибка при поиске подов", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при поиске подов: " + e.getMessage()));
        }
    }
}

