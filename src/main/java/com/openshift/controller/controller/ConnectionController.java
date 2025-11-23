package com.openshift.controller.controller;

import com.openshift.controller.entity.ConnectionGroup;
import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.service.ConnectionService;
import com.openshift.controller.service.ConnectionGroupService;
import com.openshift.controller.service.OpenShiftClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для управления конфигурацией подключения к OpenShift
 */
@Slf4j
@Controller
@RequestMapping("/connection")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectionGroupService connectionGroupService;
    private final OpenShiftClientService openShiftClientService;

    /**
     * Показать форму для настройки подключения и список всех подключений
     */
    @GetMapping("/setup")
    public String showSetupForm(
            @RequestParam(required = false) Long editId,
            Model model) {
        // Получаем все подключения
        var allConnections = connectionService.getAllConnections();
        model.addAttribute("connections", allConnections);
        
        // Получаем все группы для выбора
        var allGroups = connectionGroupService.getAllGroups();
        model.addAttribute("groups", allGroups);
        
        // Проверяем, есть ли активное подключение
        boolean hasActiveConnection = connectionService.hasActiveConnection();
        model.addAttribute("hasActiveConnection", hasActiveConnection);
        
        // Если указан ID для редактирования, загружаем это подключение
        if (editId != null) {
            allConnections.stream()
                    .filter(c -> c.getId().equals(editId))
                    .findFirst()
                    .ifPresent(conn -> model.addAttribute("editingConnection", conn));
        }
        
        // Активное подключение для справки
        connectionService.getActiveConnection().ifPresent(conn -> {
            model.addAttribute("activeConnection", conn);
        });
        
        return "connection-setup";
    }

    /**
     * Сохранить новое подключение
     */
    @PostMapping("/save")
    public String saveConnection(
            @RequestParam String masterUrl,
            @RequestParam String token,
            @RequestParam String namespace,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String newGroupName,
            @RequestParam(required = false, defaultValue = "false") boolean makeActive,
            @RequestParam(required = false, defaultValue = "false") boolean isMock,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Если нет активных подключений, новое становится активным
            boolean shouldBeActive = makeActive || !connectionService.hasActiveConnection();
            
            OpenShiftConnection connection = OpenShiftConnection.builder()
                    .masterUrl(masterUrl.trim())
                    .token(token.trim())
                    .namespace(namespace != null ? namespace.trim() : "default")
                    .name(name != null && !name.trim().isEmpty() ? name.trim() : "Подключение " + System.currentTimeMillis())
                    .active(shouldBeActive)
                    .isMock(isMock)
                    .build();
            
            // Устанавливаем группу: либо выбираем существующую, либо создаем новую
            if (groupId != null && !groupId.isEmpty() && !groupId.equals("__NEW__")) {
                try {
                    Long groupIdLong = Long.parseLong(groupId);
                    connectionGroupService.getGroupById(groupIdLong).ifPresent(connection::setGroup);
                } catch (NumberFormatException e) {
                    log.warn("Некорректный ID группы: {}", groupId);
                }
            }
            
            // Если указано название новой группы, создаем её
            if (newGroupName != null && !newGroupName.trim().isEmpty()) {
                ConnectionGroup newGroup = connectionGroupService.createGroup(newGroupName.trim(), null);
                connection.setGroup(newGroup);
                log.info("Создана новая группа '{}' для подключения", newGroupName.trim());
            }
            
            connectionService.saveConnection(connection);
            
            // Сбрасываем кэш клиента только если новое подключение активно
            if (shouldBeActive) {
                openShiftClientService.resetCache();
                
                // Для mock-подключений не проверяем реальное соединение
                if (isMock) {
                    redirectAttributes.addFlashAttribute("success", 
                        "Mock-подключение успешно сохранено и активировано! Используются тестовые данные.");
                } else {
                    // Проверяем подключение
                    try {
                        openShiftClientService.getClient();
                        redirectAttributes.addFlashAttribute("success", 
                            "Подключение успешно сохранено, активировано и проверено!");
                    } catch (Exception e) {
                        log.warn("Не удалось проверить подключение", e);
                        redirectAttributes.addFlashAttribute("warning", 
                            "Подключение сохранено и активировано, но не удалось проверить соединение: " + e.getMessage());
                    }
                }
            } else {
                String message = isMock 
                    ? "Mock-подключение успешно сохранено! Для использования активируйте его."
                    : "Подключение успешно сохранено! Для использования активируйте его.";
                redirectAttributes.addFlashAttribute("success", message);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при сохранении подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при сохранении подключения: " + e.getMessage());
        }
        
        return "redirect:/connection/setup";
    }

    /**
     * Создать тестовое mock-подключение
     */
    @PostMapping("/create-mock")
    public String createMockConnection(RedirectAttributes redirectAttributes) {
        try {
            // Если нет активных подключений, новое становится активным
            boolean shouldBeActive = !connectionService.hasActiveConnection();
            
            OpenShiftConnection mockConnection = OpenShiftConnection.builder()
                    .masterUrl("mock://test-cluster")
                    .token("mock-token-for-testing")
                    .namespace("test-project")
                    .name("Тестовый кластер (Mock)")
                    .active(shouldBeActive)
                    .isMock(true)
                    .build();
            
            connectionService.saveConnection(mockConnection);
            
            if (shouldBeActive) {
                openShiftClientService.resetCache();
                redirectAttributes.addFlashAttribute("success", 
                    "Тестовое mock-подключение успешно создано и активировано! Используются тестовые данные.");
            } else {
                redirectAttributes.addFlashAttribute("success", 
                    "Тестовое mock-подключение успешно создано! Для использования активируйте его.");
            }
            
        } catch (Exception e) {
            log.error("Ошибка при создании mock-подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при создании mock-подключения: " + e.getMessage());
        }
        
        return "redirect:/connection/setup";
    }

    /**
     * Обновить существующее подключение
     */
    @PostMapping("/update")
    public String updateConnection(
            @RequestParam Long id,
            @RequestParam String masterUrl,
            @RequestParam String token,
            @RequestParam String namespace,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String newGroupName,
            @RequestParam(required = false, defaultValue = "false") boolean isMock,
            RedirectAttributes redirectAttributes) {
        
        try {
            OpenShiftConnection updated = OpenShiftConnection.builder()
                    .masterUrl(masterUrl.trim())
                    .token(token.trim())
                    .namespace(namespace != null ? namespace.trim() : "default")
                    .name(name != null && !name.trim().isEmpty() ? name.trim() : "Основное подключение")
                    .active(true)
                    .isMock(isMock)
                    .build();
            
            // Устанавливаем группу: либо выбираем существующую, либо создаем новую, либо удаляем связь
            if (groupId != null && !groupId.isEmpty() && !groupId.equals("__NEW__")) {
                // Выбираем существующую группу
                try {
                    Long groupIdLong = Long.parseLong(groupId);
                    connectionGroupService.getGroupById(groupIdLong).ifPresent(updated::setGroup);
                } catch (NumberFormatException e) {
                    log.warn("Некорректный ID группы: {}", groupId);
                    updated.setGroup(null);
                }
            } else {
                // Если groupId не указан или равен "__NEW__" без названия, убираем связь с группой
                updated.setGroup(null);
            }
            
            // Если указано название новой группы, создаем её (приоритет над выбором существующей)
            if (newGroupName != null && !newGroupName.trim().isEmpty()) {
                ConnectionGroup newGroup = connectionGroupService.createGroup(newGroupName.trim(), null);
                updated.setGroup(newGroup);
                log.info("Создана новая группа '{}' для подключения", newGroupName.trim());
            }
            
            connectionService.updateConnection(id, updated);
            
            // Сбрасываем кэш клиента
            openShiftClientService.resetCache();
            
            redirectAttributes.addFlashAttribute("success", 
                "Подключение успешно обновлено!");
            
            // Для mock-подключений не проверяем реальное соединение
            if (!isMock) {
                // Проверяем подключение
                try {
                    openShiftClientService.getClient();
                } catch (Exception e) {
                    log.warn("Не удалось проверить подключение", e);
                    redirectAttributes.addFlashAttribute("warning", 
                        "Подключение обновлено, но не удалось проверить соединение: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при обновлении подключения: " + e.getMessage());
        }
        
        return "redirect:/connection/setup";
    }

    /**
     * Активировать подключение
     */
    @PostMapping("/activate")
    public String activateConnection(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes) {
        try {
            connectionService.activateConnection(id);
            openShiftClientService.resetCache();
            
            redirectAttributes.addFlashAttribute("success", 
                "Подключение успешно активировано!");
            
            // Проверяем подключение
            try {
                openShiftClientService.getClient();
            } catch (Exception e) {
                log.warn("Не удалось проверить подключение", e);
                redirectAttributes.addFlashAttribute("warning", 
                    "Подключение активировано, но не удалось проверить соединение: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка при активации подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при активации подключения: " + e.getMessage());
        }
        
        return "redirect:/connection/setup";
    }

    /**
     * Удалить подключение
     */
    @PostMapping("/delete")
    public String deleteConnection(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes) {
        try {
            // Проверяем, не является ли это активным подключением
            connectionService.getActiveConnection().ifPresent(active -> {
                if (active.getId().equals(id)) {
                    openShiftClientService.resetCache();
                }
            });
            
            connectionService.deleteConnection(id);
            redirectAttributes.addFlashAttribute("success", 
                "Подключение успешно удалено!");
        } catch (Exception e) {
            log.error("Ошибка при удалении подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при удалении подключения: " + e.getMessage());
        }
        
        return "redirect:/connection/setup";
    }
}

