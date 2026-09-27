package com.tradeBytes.toDoListService.controller;

import com.tradeBytes.toDoListService.dto.CreateItemRequest;
import com.tradeBytes.toDoListService.dto.UpdateDescriptionRequest;
import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.service.ToDoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/toDoItems")
public class ToDoController {

    private static final Logger log = LoggerFactory.getLogger(ToDoController.class);

    private final ToDoService toDoService;

    public ToDoController(ToDoService toDoService) {
        this.toDoService = toDoService;
    }

    @PostMapping("/addItem")
    public ResponseEntity<ToDoItem> addItem(@RequestBody CreateItemRequest createItemRequest) {
        log.info("POST /addItem - dueDateTime={}", createItemRequest.dueDateTime());
        return ResponseEntity.ok(toDoService.addItem(createItemRequest));
    }

    @PatchMapping("/{id}/MarkDone")
    public ResponseEntity<ToDoItem> markAsDone(@PathVariable UUID id) {
        log.info("PATCH /{}/MarkDone", id);
        return ResponseEntity.ok(toDoService.updateStatus(id, Status.DONE));
    }

    @PatchMapping("/{id}/MarkNotdone")
    public ResponseEntity<ToDoItem> markAsNotDone(@PathVariable UUID id) {
        log.info("PATCH /{}/MarkNotdone", id);
        return ResponseEntity.ok(toDoService.updateStatus(id, Status.NOT_DONE));
    }

    @PatchMapping("/{id}/updateDescription")
    public ResponseEntity<ToDoItem> updateDescription(@PathVariable UUID id,
                                                      @RequestBody UpdateDescriptionRequest request) {
        log.info("PATCH /{}/updateDescription", id);
        return ResponseEntity.ok(toDoService.updateDescription(id, request.description()));
    }

    @GetMapping("/getAllItems")
    public ResponseEntity<List<ToDoItem>> getItems(@RequestParam(defaultValue = "false") boolean fetchAll) {
        log.info("GET /getAllItems - fetchAll={}", fetchAll);
        return ResponseEntity.ok(toDoService.getItems(fetchAll));
    }

    @GetMapping("/{id}/getItem")
    public ResponseEntity<List<ToDoItem>> getItem(@PathVariable UUID id) {
        log.info("GET /{}/getItem", id);
        return ResponseEntity.ok(Collections.singletonList(toDoService.getItem(id)));
    }

}
