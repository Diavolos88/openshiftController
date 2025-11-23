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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web контроллер для управления Deployments
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DeploymentWebController {

    private final DeploymentService deploymentService;
    private final ConnectionService connectionService;
    private final ConnectionGroupService connectionGroupService;
    private final StateService stateService;
    private final PodService podService;

    /**
     * Главная страница - список всех групп с подключениями
     */
    @GetMapping("/")
    public String index(Model model) {
        // Получаем все группы
        List<ConnectionGroup> groups = connectionGroupService.getAllGroups();
        
        // Получаем все подключения
        List<OpenShiftConnection> allConnections = connectionService.getAllConnections();
        
        // Разделяем подключения на группы и без группы
        Map<Long, List<OpenShiftConnection>> connectionsByGroup = allConnections.stream()
                .filter(conn -> conn.getGroup() != null)
                .collect(Collectors.groupingBy(conn -> conn.getGroup().getId()));
        
        List<OpenShiftConnection> connectionsWithoutGroup = allConnections.stream()
                .filter(conn -> conn.getGroup() == null)
                .toList();
        
        // Проверяем, есть ли хотя бы одно подключение
        if (allConnections.isEmpty()) {
            // Если подключения не настроены, перенаправляем на страницу настройки
            return "redirect:/connection/setup";
        }
        
        // Создаем список групп с их подключениями
        List<GroupWithConnections> groupsWithConnections = groups.stream()
                .map(group -> {
                    List<OpenShiftConnection> groupConnections = connectionsByGroup.getOrDefault(group.getId(), List.of());
                    List<ConnectionWithNamespace> connectionsWithNamespace = groupConnections.stream()
                            .map(conn -> createConnectionWithNamespace(conn))
                            .toList();
                    return new GroupWithConnections(group, connectionsWithNamespace);
                })
                .filter(g -> !g.getConnections().isEmpty()) // Показываем только группы с подключениями
                .toList();
        
        // Создаем список подключений без группы
        List<ConnectionWithNamespace> connectionsWithoutGroupWithNamespace = connectionsWithoutGroup.stream()
                .map(this::createConnectionWithNamespace)
                .toList();
        
        model.addAttribute("groupsWithConnections", groupsWithConnections);
        model.addAttribute("connectionsWithoutGroup", connectionsWithoutGroupWithNamespace);
        model.addAttribute("allConnections", allConnections);
        
        return "namespaces";
    }

    /**
     * Создать ConnectionWithNamespace из подключения
     */
    private ConnectionWithNamespace createConnectionWithNamespace(OpenShiftConnection conn) {
        try {
            // Поскольку одно подключение = один namespace, просто используем namespace из подключения
            String namespace = conn.getNamespace() != null ? conn.getNamespace() : "default";
            // Проверяем, что namespace доступен (для mock используем mock-данные)
            return new ConnectionWithNamespace(conn, namespace, null);
        } catch (Exception e) {
            log.error("Ошибка при обработке подключения {} (ID: {})", conn.getName(), conn.getId(), e);
            return new ConnectionWithNamespace(conn, conn.getNamespace() != null ? conn.getNamespace() : "default", 
                    "Ошибка: " + e.getMessage());
        }
    }

    /**
     * DTO для группы с подключениями
     */
    public static class GroupWithConnections {
        private final ConnectionGroup group;
        private final List<ConnectionWithNamespace> connections;

        public GroupWithConnections(ConnectionGroup group, List<ConnectionWithNamespace> connections) {
            this.group = group;
            this.connections = connections;
        }

        public ConnectionGroup getGroup() {
            return group;
        }

        public List<ConnectionWithNamespace> getConnections() {
            return connections;
        }
    }

    /**
     * DTO для подключения с namespace (одно подключение = один namespace)
     */
    public static class ConnectionWithNamespace {
        private final OpenShiftConnection connection;
        private final String namespace;
        private final String error;

        public ConnectionWithNamespace(OpenShiftConnection connection, String namespace, String error) {
            this.connection = connection;
            this.namespace = namespace;
            this.error = error;
        }

        public OpenShiftConnection getConnection() {
            return connection;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * Страница управления Deployments в namespace для конкретного подключения
     */
    @GetMapping("/deployments/{connectionId}/{namespace}")
    public String deployments(
            @PathVariable Long connectionId,
            @PathVariable String namespace, 
            Model model) {
        try {
            // Получаем информацию о подключении
            connectionService.getConnectionById(connectionId).ifPresent(conn -> {
                model.addAttribute("connection", conn);
                // Строим URL консоли из API URL
                String consoleUrl = ConsoleUrlBuilder.buildConsoleUrl(conn.getMasterUrl(), namespace);
                model.addAttribute("consoleUrl", consoleUrl);
            });
            
            List<DeploymentInfo> deployments = deploymentService.getAllDeployments(connectionId, namespace);
            
            // Сохраняем стартовые значения только при первом подключении к namespace (если еще не сохранено)
            // После этого сохранение происходит только по кнопке "Обновить стартовые значения"
            if (!deployments.isEmpty() && !deploymentService.hasState(connectionId, namespace)) {
                deploymentService.saveCurrentState(connectionId, namespace);
                log.info("Автоматически сохранены стартовые значения при первом подключении для подключения ID: {}, namespace: {}", connectionId, namespace);
            }
            
            // Получаем дату последнего обновления стартовых значений
            java.time.LocalDateTime lastUpdateDate = stateService.getLastUpdateDate(connectionId, namespace);
            
            model.addAttribute("deployments", deployments);
            model.addAttribute("namespace", namespace);
            model.addAttribute("connectionId", connectionId);
            model.addAttribute("lastUpdateDate", lastUpdateDate);
        } catch (RuntimeException e) {
            log.error("Ошибка при получении списка Deployments в namespace '{}' для подключения ID: {}", namespace, connectionId, e);
            // Проверяем, содержит ли сообщение об ошибке информацию о доступе
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Нет доступа")) {
                model.addAttribute("error", errorMessage);
            } else {
                model.addAttribute("error", "Ошибка при получении списка Deployments: " + errorMessage);
            }
            model.addAttribute("deployments", List.of());
            model.addAttribute("namespace", namespace);
            model.addAttribute("connectionId", connectionId);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при получении списка Deployments", e);
            model.addAttribute("error", "Неожиданная ошибка: " + e.getMessage());
            model.addAttribute("deployments", List.of());
            model.addAttribute("namespace", namespace);
            model.addAttribute("connectionId", connectionId);
        }
        return "deployments";
    }

    /**
     * Страница управления Deployments в namespace (для обратной совместимости - использует активное подключение)
     */
    @GetMapping("/deployments/{namespace}")
    public String deploymentsLegacy(@PathVariable String namespace, Model model) {
        // Перенаправляем на новый формат с connectionId
        return connectionService.getActiveConnection()
                .map(conn -> "redirect:/deployments/" + conn.getId() + "/" + namespace)
                .orElse("redirect:/connection/setup");
    }

    /**
     * Масштабирование Deployment
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/{name}/scale")
    public String scaleDeployment(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam int replicas,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.scaleDeployment(connectionId, namespace, name, replicas);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Deployment " + name + " масштабирован до " + replicas + " реплик");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось масштабировать Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при масштабировании Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при масштабировании: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Масштабирование Deployment (для обратной совместимости)
     */
    @PostMapping("/deployments/{namespace}/{name}/scale")
    public String scaleDeploymentLegacy(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam int replicas,
            @RequestParam(required = false) Long connectionId,
            RedirectAttributes redirectAttributes) {
        Long connId = connectionId != null ? connectionId : 
            connectionService.getActiveConnection().map(OpenShiftConnection::getId).orElse(null);
        if (connId == null) {
            redirectAttributes.addFlashAttribute("error", "Подключение не найдено");
            return "redirect:/";
        }
        return "redirect:/deployments/" + connId + "/" + namespace + "/" + name + "/scale?replicas=" + replicas;
    }

    /**
     * Перезапустить все Deployments для конкретного подключения
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/restart-all")
    public String restartAll(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.restartAllDeployments(connectionId, namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Перезапущено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при перезапуске всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Перезапустить все Deployments (для обратной совместимости - без connectionId)
     */
    @PostMapping("/deployments/{namespace}/restart-all")
    public String restartAllLegacy(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.restartAllDeployments(namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Перезапущено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при перезапуске всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Остановить все Deployments (установить в 0) для конкретного подключения
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/shutdown-all")
    public String shutdownAll(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.shutdownAllDeployments(connectionId, namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Остановлено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при остановке всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Остановить все Deployments (установить в 0) (для обратной совместимости - без connectionId)
     */
    @PostMapping("/deployments/{namespace}/shutdown-all")
    public String shutdownAllLegacy(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.shutdownAllDeployments(namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Остановлено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при остановке всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Восстановить изначальное состояние для конкретного подключения
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/restore")
    public String restoreAll(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        log.info("Вызов restoreAll для connectionId={}, namespace={}, groupId={}", connectionId, namespace, groupId);
        try {
            Map<String, Boolean> results = deploymentService.restoreAllDeployments(connectionId, namespace);
            if (results.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Нет сохраненного состояния для восстановления");
            } else {
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Восстановлено " + successCount + " из " + results.size() + " Deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении состояния", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Восстановить изначальное состояние (для обратной совместимости - без connectionId)
     */
    @PostMapping("/deployments/{namespace}/restore")
    public String restoreAllLegacy(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.restoreAllDeployments(namespace);
            if (results.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Нет сохраненного состояния для восстановления");
            } else {
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Восстановлено " + successCount + " из " + results.size() + " Deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении состояния", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Перезапустить все поды для конкретного Deployment (с connectionId)
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/{name}/pods/restart")
    public String restartPodsForDeployment(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.restartAllPodsForDeployment(connectionId, namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Поды для Deployment " + name + " перезапущены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось перезапустить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске подов: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Перезапустить все поды для конкретного Deployment (для обратной совместимости)
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/restart")
    public String restartPodsForDeploymentLegacy(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        return connectionService.getActiveConnection()
                .map(conn -> {
                    try {
                        boolean success = deploymentService.restartAllPodsForDeployment(conn.getId(), namespace, name);
                        if (success) {
                            redirectAttributes.addFlashAttribute("success", 
                                "Поды для Deployment " + name + " перезапущены");
                        } else {
                            redirectAttributes.addFlashAttribute("error", 
                                "Не удалось перезапустить поды для Deployment " + name);
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при перезапуске подов Deployment", e);
                        redirectAttributes.addFlashAttribute("error", 
                            "Ошибка при перезапуске подов: " + e.getMessage());
                    }
                    return "redirect:/deployments/" + namespace;
                })
                .orElse("redirect:/connection/setup");
    }

    /**
     * Удалить все поды для конкретного Deployment (с connectionId)
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/{name}/pods/delete")
    public String deletePodsForDeployment(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.deleteAllPodsForDeployment(connectionId, namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Все поды для Deployment " + name + " удалены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось удалить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при удалении подов: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Удалить все поды для конкретного Deployment (для обратной совместимости)
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/delete")
    public String deletePodsForDeploymentLegacy(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        return connectionService.getActiveConnection()
                .map(conn -> {
                    try {
                        boolean success = deploymentService.deleteAllPodsForDeployment(conn.getId(), namespace, name);
                        if (success) {
                            redirectAttributes.addFlashAttribute("success", 
                                "Все поды для Deployment " + name + " удалены");
                        } else {
                            redirectAttributes.addFlashAttribute("error", 
                                "Не удалось удалить поды для Deployment " + name);
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при удалении подов Deployment", e);
                        redirectAttributes.addFlashAttribute("error", 
                            "Ошибка при удалении подов: " + e.getMessage());
                    }
                    return "redirect:/deployments/" + namespace;
                })
                .orElse("redirect:/connection/setup");
    }

    /**
     * Восстановить поды для конкретного Deployment (с connectionId)
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/{name}/pods/restore")
    public String restorePodsForDeployment(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.restorePodsForDeployment(connectionId, namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Поды для Deployment " + name + " восстановлены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось восстановить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении подов: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Замерить время старта пода для конкретного Deployment (с connectionId)
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/{name}/measure-startup")
    public String measurePodStartupTime(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            Long startupTime = podService.measurePodStartupTime(connectionId, namespace, name);
            if (startupTime != null) {
                redirectAttributes.addFlashAttribute("success", 
                    "Время старта пода для Deployment " + name + ": " + startupTime + " секунд");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось замерить время старта пода для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при замере времени старта пода для Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при замере времени старта: " + e.getMessage());
        }
        
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Восстановить поды для конкретного Deployment (для обратной совместимости)
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/restore")
    public String restorePodsForDeploymentLegacy(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        return connectionService.getActiveConnection()
                .map(conn -> {
                    try {
                        boolean success = deploymentService.restorePodsForDeployment(conn.getId(), namespace, name);
                        if (success) {
                            redirectAttributes.addFlashAttribute("success", 
                                "Поды для Deployment " + name + " восстановлены");
                        } else {
                            redirectAttributes.addFlashAttribute("error", 
                                "Не удалось восстановить поды для Deployment " + name);
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при восстановлении подов Deployment", e);
                        redirectAttributes.addFlashAttribute("error", 
                            "Ошибка при восстановлении подов: " + e.getMessage());
                    }
                    return "redirect:/deployments/" + namespace;
                })
                .orElse("redirect:/connection/setup");
    }

    /**
     * Замерить время старта подов для выбранных Deployments
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/batch/measure-startup")
    public String measureStartupTimeForSelected(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam List<String> deploymentNames,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Long> results = podService.measurePodStartupTimeForDeployments(
                    connectionId, namespace, deploymentNames);
            
            StringBuilder message = new StringBuilder("Время старта подов измерено: ");
            results.forEach((name, time) -> {
                if (time != null) {
                    message.append(name).append(": ").append(time).append(" сек; ");
                } else {
                    message.append(name).append(": не удалось замерить; ");
                }
            });
            
            redirectAttributes.addFlashAttribute("success", message.toString());
        } catch (Exception e) {
            log.error("Ошибка при замере времени старта подов для выбранных deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при замере времени старта: " + e.getMessage());
        }
        
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Обновить стартовые значения для всех Deployments в namespace
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/update-original-state")
    public String updateOriginalState(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            deploymentService.saveCurrentState(connectionId, namespace);
            redirectAttributes.addFlashAttribute("success", 
                "Стартовые значения успешно обновлены для всех Deployments в namespace " + namespace);
        } catch (Exception e) {
            log.error("Ошибка при обновлении стартовых значений", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при обновлении стартовых значений: " + e.getMessage());
        }
        // Если запрос из группы, возвращаемся в группу, иначе на страницу deployments
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Массовое удаление выбранных Deployments (остановка - установка в 0)
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/batch/shutdown")
    public String shutdownSelectedDeployments(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) List<String> deploymentNames,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            if (deploymentNames == null || deploymentNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для остановки");
            } else {
                Map<String, Boolean> results = deploymentService.shutdownSelectedDeployments(connectionId, namespace, deploymentNames);
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Остановлено " + successCount + " из " + deploymentNames.size() + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовой остановке deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Массовый перезапуск выбранных Deployments
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/batch/restart")
    public String restartSelectedDeployments(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) List<String> deploymentNames,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            if (deploymentNames == null || deploymentNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для перезапуска");
            } else {
                Map<String, Boolean> results = deploymentService.restartSelectedDeployments(connectionId, namespace, deploymentNames);
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Перезапущено " + successCount + " из " + deploymentNames.size() + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом перезапуске deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Массовое восстановление выбранных Deployments
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/batch/restore")
    public String restoreSelectedDeployments(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) List<String> deploymentNames,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            if (deploymentNames == null || deploymentNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны deployments для восстановления");
            } else {
                Map<String, Boolean> results = deploymentService.restoreSelectedDeployments(connectionId, namespace, deploymentNames);
                if (results.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", 
                        "Нет сохраненного состояния для восстановления");
                } else {
                    long successCount = results.values().stream().filter(b -> b).count();
                    redirectAttributes.addFlashAttribute("success", 
                        "Восстановлено " + successCount + " из " + results.size() + " deployments");
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом восстановлении deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }

    /**
     * Массовое масштабирование нескольких Deployments
     * Формат scalingData: "deploymentName|replicas,deploymentName|replicas,..."
     */
    @PostMapping("/deployments/{connectionId}/{namespace}/batch/scale")
    public String scaleSelectedDeployments(
            @PathVariable Long connectionId,
            @PathVariable String namespace,
            @RequestParam(required = false) List<String> scalingData,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes redirectAttributes) {
        try {
            if (scalingData == null || scalingData.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Нет изменений для сохранения");
            } else {
                Map<String, Boolean> results = new HashMap<>();
                int totalProcessed = 0;
                
                for (String data : scalingData) {
                    String[] parts = data.split("\\|");
                    if (parts.length == 2) {
                        try {
                            String deploymentName = parts[0];
                            int replicas = Integer.parseInt(parts[1]);
                            
                            boolean success = deploymentService.scaleDeployment(connectionId, namespace, deploymentName, replicas);
                            results.put(deploymentName, success);
                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Ошибка при масштабировании deployment: {}", data, e);
                            results.put(parts[0], false);
                        }
                    }
                }
                
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Масштабировано " + successCount + " из " + totalProcessed + " deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом масштабировании deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при масштабировании: " + e.getMessage());
        }
        if (groupId != null) {
            return "redirect:/groups/" + groupId;
        }
        return "redirect:/deployments/" + connectionId + "/" + namespace;
    }
}

