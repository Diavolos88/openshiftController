package com.openshift.controller.controller;

import com.openshift.controller.dto.DeploymentInfo;
import com.openshift.controller.entity.ConnectionGroup;
import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.service.ConnectionGroupService;
import com.openshift.controller.service.ConnectionService;
import com.openshift.controller.service.DeploymentService;
import com.openshift.controller.service.PodService;
import com.openshift.controller.service.StateService;
import com.openshift.controller.util.ConsoleUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web контроллер для управления группами подключений
 */
@Slf4j
@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupWebController {

    private final ConnectionGroupService groupService;
    private final ConnectionService connectionService;
    private final DeploymentService deploymentService;
    private final PodService podService;
    private final StateService stateService;

    /**
     * Страница просмотра группы с подключениями и их deployments
     */
    @GetMapping("/{groupId}")
    public String viewGroup(@PathVariable Long groupId, Model model) {
        try {
            // Получаем группу
            ConnectionGroup group = groupService.getGroupById(groupId)
                    .orElseThrow(() -> new RuntimeException("Группа с ID " + groupId + " не найдена"));
            
            // Получаем все подключения группы
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            
            if (connections.isEmpty()) {
                model.addAttribute("error", "В группе нет подключений");
                model.addAttribute("group", group);
                return "group-view";
            }
            
            // Создаем список подключений с их deployments
            List<ConnectionWithDeployments> connectionsWithDeployments = new ArrayList<>();
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    List<DeploymentInfo> deployments = deploymentService.getAllDeployments(conn.getId(), namespace);
                    
                    // Сохраняем стартовые значения только при первом подключении к namespace (если еще не сохранено)
                    // После этого сохранение происходит только по кнопке "Обновить стартовые значения"
                    if (!deployments.isEmpty() && !deploymentService.hasState(conn.getId(), namespace)) {
                        deploymentService.saveCurrentState(conn.getId(), namespace);
                        log.info("Автоматически сохранены стартовые значения при первом подключении для подключения ID: {}, namespace: {}", 
                                conn.getId(), namespace);
                    }
                    
                    // Получаем дату последнего обновления стартовых значений
                    java.time.LocalDateTime lastUpdateDate = stateService.getLastUpdateDate(conn.getId(), namespace);
                    // Строим URL консоли из API URL
                    String consoleUrl = ConsoleUrlBuilder.buildConsoleUrl(conn.getMasterUrl(), namespace);
                    connectionsWithDeployments.add(new ConnectionWithDeployments(conn, namespace, deployments, null, lastUpdateDate, consoleUrl));
                } catch (Exception e) {
                    log.error("Ошибка при получении deployments для подключения {} (ID: {})", conn.getName(), conn.getId(), e);
                    String errorNamespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    java.time.LocalDateTime lastUpdateDate = stateService.getLastUpdateDate(conn.getId(), errorNamespace);
                    // Строим URL консоли из API URL даже при ошибке
                    String consoleUrl = ConsoleUrlBuilder.buildConsoleUrl(conn.getMasterUrl(), errorNamespace);
                    connectionsWithDeployments.add(new ConnectionWithDeployments(conn, errorNamespace, List.of(), 
                            "Ошибка: " + e.getMessage(), lastUpdateDate, consoleUrl));
                }
            }
            
            model.addAttribute("group", group);
            model.addAttribute("connectionsWithDeployments", connectionsWithDeployments);
            model.addAttribute("connectionsCount", connections.size());
            
        } catch (Exception e) {
            log.error("Ошибка при получении информации о группе ID: {}", groupId, e);
            model.addAttribute("error", "Ошибка при получении информации о группе: " + e.getMessage());
        }
        
        return "group-view";
    }

    /**
     * Массовый перезапуск всех deployments во всей группе
     */
    @PostMapping("/{groupId}/restart-all")
    public String restartAllInGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        try {
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            Map<String, Boolean> results = new HashMap<>();
            
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    Map<String, Boolean> connResults = deploymentService.restartAllDeployments(conn.getId(), namespace);
                    connResults.forEach((name, success) -> 
                        results.put(conn.getName() + "/" + namespace + "/" + name, success));
                } catch (Exception e) {
                    log.error("Ошибка при перезапуске deployments для подключения {} (ID: {})", conn.getName(), conn.getId(), e);
                }
            }
            
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Перезапущено " + successCount + " из " + results.size() + " deployments в группе");
        } catch (Exception e) {
            log.error("Ошибка при массовом перезапуске группы ID: {}", groupId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовая остановка всех deployments во всей группе (установка в 0)
     */
    @PostMapping("/{groupId}/shutdown-all")
    public String shutdownAllInGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        try {
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            Map<String, Boolean> results = new HashMap<>();
            
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    Map<String, Boolean> connResults = deploymentService.shutdownAllDeployments(conn.getId(), namespace);
                    connResults.forEach((name, success) -> 
                        results.put(conn.getName() + "/" + namespace + "/" + name, success));
                } catch (Exception e) {
                    log.error("Ошибка при остановке deployments для подключения {} (ID: {})", conn.getName(), conn.getId(), e);
                }
            }
            
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Остановлено " + successCount + " из " + results.size() + " deployments в группе");
        } catch (Exception e) {
            log.error("Ошибка при массовой остановке группы ID: {}", groupId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Восстановление изначального состояния всех deployments во всей группе
     */
    @PostMapping("/{groupId}/restore")
    public String restoreAllInGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        try {
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            Map<String, Boolean> results = new HashMap<>();
            
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    Map<String, Boolean> connResults = deploymentService.restoreAllDeployments(conn.getId(), namespace);
                    connResults.forEach((name, success) -> 
                        results.put(conn.getName() + "/" + namespace + "/" + name, success));
                } catch (Exception e) {
                    log.error("Ошибка при восстановлении deployments для подключения {} (ID: {})", conn.getName(), conn.getId(), e);
                }
            }
            
            if (results.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Нет сохраненного состояния для восстановления");
            } else {
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Восстановлено " + successCount + " из " + results.size() + " deployments в группе");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом восстановлении группы ID: {}", groupId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовое масштабирование всех deployments во всей группе
     */
    @PostMapping("/{groupId}/scale-all")
    public String scaleAllInGroup(
            @PathVariable Long groupId,
            @RequestParam int replicas,
            RedirectAttributes redirectAttributes) {
        try {
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            Map<String, Boolean> results = new HashMap<>();
            
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    List<DeploymentInfo> deployments = deploymentService.getAllDeployments(conn.getId(), namespace);
                    
                    for (DeploymentInfo deployment : deployments) {
                        try {
                            boolean success = deploymentService.scaleDeployment(conn.getId(), namespace, deployment.getName(), replicas);
                            results.put(conn.getName() + "/" + namespace + "/" + deployment.getName(), success);
                        } catch (Exception e) {
                            log.error("Ошибка при масштабировании deployment {} в подключении {} (ID: {})", 
                                    deployment.getName(), conn.getName(), conn.getId(), e);
                            results.put(conn.getName() + "/" + namespace + "/" + deployment.getName(), false);
                        }
                    }
                } catch (Exception e) {
                    log.error("Ошибка при получении deployments для подключения {} (ID: {})", conn.getName(), conn.getId(), e);
                }
            }
            
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Масштабировано " + successCount + " из " + results.size() + " deployments до " + replicas + " реплик в группе");
        } catch (Exception e) {
            log.error("Ошибка при массовом масштабировании группы ID: {}", groupId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при масштабировании: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Индивидуальные операции для конкретного подключения в группе
     */
    
    /**
     * Перезапуск всех deployments для конкретного подключения
     */
    @PostMapping("/{groupId}/connections/{connectionId}/restart-all")
    public String restartAllForConnection(
            @PathVariable Long groupId,
            @PathVariable Long connectionId,
            RedirectAttributes redirectAttributes) {
        try {
            OpenShiftConnection conn = connectionService.getConnectionById(connectionId)
                    .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
            
            String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
            Map<String, Boolean> results = deploymentService.restartAllDeployments(connectionId, namespace);
            
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Перезапущено " + successCount + " из " + results.size() + " deployments для проекта '" + conn.getName() + "'");
        } catch (Exception e) {
            log.error("Ошибка при перезапуске deployments для подключения ID: {}", connectionId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Остановка всех deployments для конкретного подключения
     */
    @PostMapping("/{groupId}/connections/{connectionId}/shutdown-all")
    public String shutdownAllForConnection(
            @PathVariable Long groupId,
            @PathVariable Long connectionId,
            RedirectAttributes redirectAttributes) {
        try {
            OpenShiftConnection conn = connectionService.getConnectionById(connectionId)
                    .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
            
            String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
            Map<String, Boolean> results = deploymentService.shutdownAllDeployments(connectionId, namespace);
            
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Остановлено " + successCount + " из " + results.size() + " deployments для проекта '" + conn.getName() + "'");
        } catch (Exception e) {
            log.error("Ошибка при остановке deployments для подключения ID: {}", connectionId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Восстановление всех deployments для конкретного подключения
     */
    @PostMapping("/{groupId}/connections/{connectionId}/restore")
    public String restoreAllForConnection(
            @PathVariable Long groupId,
            @PathVariable Long connectionId,
            RedirectAttributes redirectAttributes) {
        try {
            OpenShiftConnection conn = connectionService.getConnectionById(connectionId)
                    .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
            
            String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
            Map<String, Boolean> results = deploymentService.restoreAllDeployments(connectionId, namespace);
            
            if (results.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Нет сохраненного состояния для восстановления в проекте '" + conn.getName() + "'");
            } else {
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Восстановлено " + successCount + " из " + results.size() + " deployments для проекта '" + conn.getName() + "'");
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении deployments для подключения ID: {}", connectionId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Обновление стартовых значений для конкретного подключения
     */
    @PostMapping("/{groupId}/connections/{connectionId}/update-original-state")
    public String updateOriginalStateForConnection(
            @PathVariable Long groupId,
            @PathVariable Long connectionId,
            RedirectAttributes redirectAttributes) {
        try {
            OpenShiftConnection conn = connectionService.getConnectionById(connectionId)
                    .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
            
            String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
            deploymentService.saveCurrentState(connectionId, namespace);
            
            redirectAttributes.addFlashAttribute("success", 
                "Стартовые значения успешно обновлены для проекта '" + conn.getName() + "'");
        } catch (Exception e) {
            log.error("Ошибка при обновлении стартовых значений для подключения ID: {}", connectionId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при обновлении стартовых значений: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовое обновление стартовых значений для всей группы
     */
    @PostMapping("/{groupId}/update-original-state-all")
    public String updateOriginalStateForAll(
            @PathVariable Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            List<OpenShiftConnection> connections = groupService.getGroupConnections(groupId);
            int successCount = 0;
            int totalCount = connections.size();
            
            for (OpenShiftConnection conn : connections) {
                try {
                    String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
                    deploymentService.saveCurrentState(conn.getId(), namespace);
                    successCount++;
                } catch (Exception e) {
                    log.error("Ошибка при обновлении стартовых значений для подключения {} (ID: {})", 
                            conn.getName(), conn.getId(), e);
                }
            }
            
            if (successCount == totalCount) {
                redirectAttributes.addFlashAttribute("success", 
                    "Стартовые значения успешно обновлены для всех проектов в группе (" + successCount + " из " + totalCount + ")");
            } else {
                redirectAttributes.addFlashAttribute("success", 
                    "Стартовые значения обновлены для " + successCount + " из " + totalCount + " проектов в группе");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом обновлении стартовых значений для группы ID: {}", groupId, e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при обновлении стартовых значений: " + e.getMessage());
        }
        
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовое удаление выбранных Deployments (остановка - установка в 0) для всех подключений в группе
     * Формат selectedDeployments: "connectionId|namespace|deploymentName,connectionId|namespace|deploymentName,..."
     */
    @PostMapping("/{groupId}/batch/shutdown")
    public String shutdownSelectedDeployments(
            @PathVariable Long groupId,
            @RequestParam(required = false) List<String> selectedDeployments,
            RedirectAttributes redirectAttributes) {
        try {
            if (selectedDeployments == null || selectedDeployments.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для остановки");
            } else {
                Map<String, Boolean> allResults = new HashMap<>();
                int totalProcessed = 0;
                
                for (String deploymentData : selectedDeployments) {
                    String[] parts = deploymentData.split("\\|");
                    if (parts.length == 3) {
                        try {
                            Long connectionId = Long.parseLong(parts[0]);
                            String namespace = parts[1];
                            String deploymentName = parts[2];
                            
                            Map<String, Boolean> results = deploymentService.shutdownSelectedDeployments(
                                    connectionId, namespace, List.of(deploymentName));
                            allResults.putAll(results);
                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Ошибка при обработке deployment: {}", deploymentData, e);
                        }
                    }
                }
                
                long successCount = allResults.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Остановлено " + successCount + " из " + totalProcessed + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовой остановке deployments в группе", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовый перезапуск выбранных Deployments для всех подключений в группе
     * Формат selectedDeployments: "connectionId|namespace|deploymentName,connectionId|namespace|deploymentName,..."
     */
    @PostMapping("/{groupId}/batch/restart")
    public String restartSelectedDeployments(
            @PathVariable Long groupId,
            @RequestParam(required = false) List<String> selectedDeployments,
            RedirectAttributes redirectAttributes) {
        try {
            if (selectedDeployments == null || selectedDeployments.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для перезапуска");
            } else {
                Map<String, Boolean> allResults = new HashMap<>();
                int totalProcessed = 0;
                
                for (String deploymentData : selectedDeployments) {
                    String[] parts = deploymentData.split("\\|");
                    if (parts.length == 3) {
                        try {
                            Long connectionId = Long.parseLong(parts[0]);
                            String namespace = parts[1];
                            String deploymentName = parts[2];
                            
                            Map<String, Boolean> results = deploymentService.restartSelectedDeployments(
                                    connectionId, namespace, List.of(deploymentName));
                            allResults.putAll(results);
                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Ошибка при обработке deployment: {}", deploymentData, e);
                        }
                    }
                }
                
                long successCount = allResults.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Перезапущено " + successCount + " из " + totalProcessed + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом перезапуске deployments в группе", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовое восстановление выбранных Deployments для всех подключений в группе
     * Формат selectedDeployments: "connectionId|namespace|deploymentName,connectionId|namespace|deploymentName,..."
     */
    @PostMapping("/{groupId}/batch/restore")
    public String restoreSelectedDeployments(
            @PathVariable Long groupId,
            @RequestParam(required = false) List<String> selectedDeployments,
            RedirectAttributes redirectAttributes) {
        try {
            if (selectedDeployments == null || selectedDeployments.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для восстановления");
            } else {
                Map<String, Boolean> allResults = new HashMap<>();
                int totalProcessed = 0;
                
                for (String deploymentData : selectedDeployments) {
                    String[] parts = deploymentData.split("\\|");
                    if (parts.length == 3) {
                        try {
                            Long connectionId = Long.parseLong(parts[0]);
                            String namespace = parts[1];
                            String deploymentName = parts[2];
                            
                            Map<String, Boolean> results = deploymentService.restoreSelectedDeployments(
                                    connectionId, namespace, List.of(deploymentName));
                            allResults.putAll(results);
                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Ошибка при обработке deployment: {}", deploymentData, e);
                        }
                    }
                }
                
                if (allResults.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", 
                        "Нет сохраненного состояния для восстановления выбранных deployments");
                } else {
                    long successCount = allResults.values().stream().filter(b -> b).count();
                    redirectAttributes.addFlashAttribute("success", 
                        "Восстановлено " + successCount + " из " + totalProcessed + " deployments");
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом восстановлении deployments в группе", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовый замер времени старта подов для выбранных Deployments для всех подключений в группе
     * Формат selectedDeployments: "connectionId|namespace|deploymentName,connectionId|namespace|deploymentName,..."
     */
    @PostMapping("/{groupId}/batch/measure-startup")
    public String measureStartupTimeForSelected(
            @PathVariable Long groupId,
            @RequestParam(required = false) List<String> selectedDeployments,
            RedirectAttributes redirectAttributes) {
        try {
            if (selectedDeployments == null || selectedDeployments.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для замера времени старта");
            } else {
                Map<String, Long> allResults = new HashMap<>();
                int totalProcessed = 0;
                
                // Группируем deployments по connectionId и namespace для более эффективной обработки
                Map<String, List<String>> deploymentsByConnection = new HashMap<>();
                
                for (String deploymentData : selectedDeployments) {
                    String[] parts = deploymentData.split("\\|");
                    if (parts.length == 3) {
                        try {
                            Long connectionId = Long.parseLong(parts[0]);
                            String namespace = parts[1];
                            String deploymentName = parts[2];
                            
                            String key = connectionId + "|" + namespace;
                            deploymentsByConnection.computeIfAbsent(key, k -> new ArrayList<>()).add(deploymentName);
                        } catch (Exception e) {
                            log.error("Ошибка при обработке deployment: {}", deploymentData, e);
                        }
                    }
                }
                
                // Обрабатываем каждое подключение
                for (Map.Entry<String, List<String>> entry : deploymentsByConnection.entrySet()) {
                    String[] keyParts = entry.getKey().split("\\|");
                    if (keyParts.length == 2) {
                        try {
                            Long connectionId = Long.parseLong(keyParts[0]);
                            String namespace = keyParts[1];
                            List<String> deploymentNames = entry.getValue();
                            
                            Map<String, Long> results = podService.measurePodStartupTimeForDeployments(
                                    connectionId, namespace, deploymentNames);
                            allResults.putAll(results);
                            totalProcessed += deploymentNames.size();
                        } catch (Exception e) {
                            log.error("Ошибка при обработке deployments для подключения: {}", entry.getKey(), e);
                        }
                    }
                }
                
                long successCount = allResults.values().stream().filter(t -> t != null).count();
                StringBuilder message = new StringBuilder("Время старта подов измерено: ");
                message.append(successCount).append(" из ").append(totalProcessed).append(" deployments. ");
                
                // Добавляем детали по первым нескольким результатам
                int detailCount = 0;
                for (Map.Entry<String, Long> result : allResults.entrySet()) {
                    if (detailCount < 5) {
                        if (result.getValue() != null) {
                            message.append(result.getKey()).append(": ").append(result.getValue()).append(" сек; ");
                        } else {
                            message.append(result.getKey()).append(": не удалось; ");
                        }
                        detailCount++;
                    }
                }
                
                if (totalProcessed > 5) {
                    message.append("...");
                }
                
                redirectAttributes.addFlashAttribute("success", message.toString());
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом замере времени старта подов в группе", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при замере времени старта: " + e.getMessage());
        }
        return "redirect:/groups/" + groupId;
    }

    /**
     * Массовое масштабирование выбранных Deployments для всех подключений в группе
     * Формат scalingData: "connectionId|namespace|deploymentName|replicas,connectionId|namespace|deploymentName|replicas,..."
     */
    @PostMapping("/{groupId}/batch/scale")
    public String scaleSelectedDeployments(
            @PathVariable Long groupId,
            @RequestParam(required = false) List<String> scalingData,
            RedirectAttributes redirectAttributes) {
        try {
            if (scalingData == null || scalingData.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Нет изменений для сохранения");
            } else {
                Map<String, Boolean> allResults = new HashMap<>();
                int totalProcessed = 0;
                
                for (String data : scalingData) {
                    String[] parts = data.split("\\|");
                    if (parts.length == 4) {
                        try {
                            Long connectionId = Long.parseLong(parts[0]);
                            String namespace = parts[1];
                            String deploymentName = parts[2];
                            int replicas = Integer.parseInt(parts[3]);
                            
                            boolean success = deploymentService.scaleDeployment(connectionId, namespace, deploymentName, replicas);
                            allResults.put(connectionId + ":" + deploymentName, success);
                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Ошибка при масштабировании deployment: {}", data, e);
                        }
                    }
                }
                
                long successCount = allResults.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Масштабировано " + successCount + " из " + totalProcessed + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом масштабировании deployments в группе", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при масштабировании: " + e.getMessage());
        }
        return "redirect:/groups/" + groupId;
    }

    /**
     * DTO для подключения с deployments
     */
    public static class ConnectionWithDeployments {
        private final OpenShiftConnection connection;
        private final String namespace;
        private final List<DeploymentInfo> deployments;
        private final String error;
        private final java.time.LocalDateTime lastUpdateDate;
        private final String consoleUrl;

        public ConnectionWithDeployments(OpenShiftConnection connection, String namespace, 
                                        List<DeploymentInfo> deployments, String error, 
                                        java.time.LocalDateTime lastUpdateDate, String consoleUrl) {
            this.connection = connection;
            this.namespace = namespace;
            this.deployments = deployments;
            this.error = error;
            this.lastUpdateDate = lastUpdateDate;
            this.consoleUrl = consoleUrl;
        }

        public OpenShiftConnection getConnection() {
            return connection;
        }

        public String getNamespace() {
            return namespace;
        }

        public List<DeploymentInfo> getDeployments() {
            return deployments;
        }

        public String getError() {
            return error;
        }

        public java.time.LocalDateTime getLastUpdateDate() {
            return lastUpdateDate;
        }

        public String getConsoleUrl() {
            return consoleUrl;
        }
    }
}

