package com.openshift.controller.controller;

import com.openshift.controller.entity.ConnectionGroup;
import com.openshift.controller.service.ConnectionGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Web контроллер для управления группами подключений
 */
@Slf4j
@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class ConnectionGroupWebController {

    private final ConnectionGroupService groupService;

    /**
     * Страница списка всех групп
     */
    @GetMapping("/manage")
    public String manageGroups(Model model) {
        List<ConnectionGroup> groups = groupService.getAllGroups();
        model.addAttribute("groups", groups);
        return "groups";
    }

    /**
     * Страница создания новой группы
     */
    @GetMapping("/create")
    public String createGroupForm(Model model) {
        model.addAttribute("group", new ConnectionGroup());
        model.addAttribute("isEdit", false);
        return "group-form";
    }

    /**
     * Страница редактирования группы
     */
    @GetMapping("/edit/{id}")
    public String editGroupForm(@PathVariable Long id, Model model) {
        ConnectionGroup group = groupService.getGroupById(id)
                .orElseThrow(() -> new RuntimeException("Группа с ID " + id + " не найдена"));
        
        model.addAttribute("group", group);
        model.addAttribute("isEdit", true);
        return "group-form";
    }

    /**
     * Создать новую группу
     */
    @PostMapping("/create")
    public String createGroup(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        try {
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Название группы обязательно");
                return "redirect:/groups/create";
            }
            
            ConnectionGroup group = groupService.createGroup(name.trim(), description != null ? description.trim() : null);
            redirectAttributes.addFlashAttribute("success", "Группа '" + group.getName() + "' успешно создана");
            log.info("Создана группа подключений: {} (ID: {})", group.getName(), group.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании группы", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании группы: " + e.getMessage());
            return "redirect:/groups/create";
        }
        return "redirect:/groups/manage";
    }

    /**
     * Обновить группу
     */
    @PostMapping("/update/{id}")
    public String updateGroup(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        try {
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Название группы обязательно");
                return "redirect:/groups/edit/" + id;
            }
            
            ConnectionGroup group = groupService.updateGroup(id, name.trim(), description != null ? description.trim() : null);
            redirectAttributes.addFlashAttribute("success", "Группа '" + group.getName() + "' успешно обновлена");
            log.info("Обновлена группа подключений: {} (ID: {})", group.getName(), group.getId());
        } catch (Exception e) {
            log.error("Ошибка при обновлении группы с ID: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении группы: " + e.getMessage());
            return "redirect:/groups/edit/" + id;
        }
        return "redirect:/groups/manage";
    }

    /**
     * Удалить группу
     */
    @PostMapping("/delete/{id}")
    public String deleteGroup(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            ConnectionGroup group = groupService.getGroupById(id)
                    .orElseThrow(() -> new RuntimeException("Группа с ID " + id + " не найдена"));
            
            String groupName = group.getName();
            groupService.deleteGroup(id);
            redirectAttributes.addFlashAttribute("success", "Группа '" + groupName + "' успешно удалена");
            log.info("Удалена группа подключений: {} (ID: {})", groupName, id);
        } catch (Exception e) {
            log.error("Ошибка при удалении группы с ID: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении группы: " + e.getMessage());
        }
        return "redirect:/groups/manage";
    }
}

