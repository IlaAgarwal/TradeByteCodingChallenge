package com.tradeBytes.toDoLIstService.controller;

import com.tradeBytes.toDoLIstService.dto.CreateItemRequest;
import com.tradeBytes.toDoLIstService.dto.UpdateDescriptionRequest;
import com.tradeBytes.toDoLIstService.model.Status;
import com.tradeBytes.toDoLIstService.model.ToDoItem;
import com.tradeBytes.toDoLIstService.service.ToDoItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/items")
public class ToDoListController {

    private final ToDoItemService toDoItemService;

    public ToDoListController(ToDoItemService toDoItemService) {
        this.toDoItemService = toDoItemService;
    }

    @PostMapping("/addItem")
    public ResponseEntity<ToDoItem> addItem(@RequestBody CreateItemRequest createItemRequest) {
        return ResponseEntity.ok(toDoItemService.addItem(createItemRequest));
    }

    @PatchMapping("/{id}/MarkDone")
    public ResponseEntity<ToDoItem> markAsDone(@PathVariable UUID id) {
        return ResponseEntity.ok(toDoItemService.updateStatus(id, Status.DONE));
    }

    @PatchMapping("/{id}/MarkNotdone")
    public ResponseEntity<ToDoItem> markAsNotDone(@PathVariable UUID id) {
        return ResponseEntity.ok(toDoItemService.updateStatus(id, Status.NOT_DONE));
    }

    @PatchMapping("/{id}/updatedescription")
    public ResponseEntity<ToDoItem> updateDescription(@PathVariable UUID id,
                                                      @RequestBody UpdateDescriptionRequest request) {
        return ResponseEntity.ok(toDoItemService.updateDescription(id, request.description()));
    }

    @GetMapping("/getAllItems")
    public ResponseEntity<List<ToDoItem>> getItems(@RequestParam(defaultValue = "false") boolean fetchAll) {
        return ResponseEntity.ok(toDoItemService.getItems(fetchAll));
    }

    @GetMapping("/{id}/getItem")
    public ResponseEntity<List<ToDoItem>> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(Collections.singletonList(toDoItemService.getItem(id)));
    }

}
