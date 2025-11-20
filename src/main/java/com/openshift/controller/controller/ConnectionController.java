package com.openshift.controller.controller;

import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.service.ConnectionService;
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
            @RequestParam(required = false) String defaultNamespace,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "false") boolean makeActive,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Если нет активных подключений, новое становится активным
            boolean shouldBeActive = makeActive || !connectionService.hasActiveConnection();
            
            OpenShiftConnection connection = OpenShiftConnection.builder()
                    .masterUrl(masterUrl.trim())
                    .token(token.trim())
                    .defaultNamespace(defaultNamespace != null ? defaultNamespace.trim() : null)
                    .name(name != null && !name.trim().isEmpty() ? name.trim() : "Подключение " + System.currentTimeMillis())
                    .active(shouldBeActive)
                    .build();
            
            connectionService.saveConnection(connection);
            
            // Сбрасываем кэш клиента только если новое подключение активно
            if (shouldBeActive) {
                openShiftClientService.resetCache();
                
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
            } else {
                redirectAttributes.addFlashAttribute("success", 
                    "Подключение успешно сохранено! Для использования активируйте его.");
            }
            
        } catch (Exception e) {
            log.error("Ошибка при сохранении подключения", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при сохранении подключения: " + e.getMessage());
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
            @RequestParam(required = false) String defaultNamespace,
            @RequestParam(required = false) String name,
            RedirectAttributes redirectAttributes) {
        
        try {
            OpenShiftConnection updated = OpenShiftConnection.builder()
                    .masterUrl(masterUrl.trim())
                    .token(token.trim())
                    .defaultNamespace(defaultNamespace != null ? defaultNamespace.trim() : null)
                    .name(name != null && !name.trim().isEmpty() ? name.trim() : "Основное подключение")
                    .active(true)
                    .build();
            
            connectionService.updateConnection(id, updated);
            
            // Сбрасываем кэш клиента
            openShiftClientService.resetCache();
            
            redirectAttributes.addFlashAttribute("success", 
                "Подключение успешно обновлено!");
            
            // Проверяем подключение
            try {
                openShiftClientService.getClient();
            } catch (Exception e) {
                log.warn("Не удалось проверить подключение", e);
                redirectAttributes.addFlashAttribute("warning", 
                    "Подключение обновлено, но не удалось проверить соединение: " + e.getMessage());
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

