package com.tradeBytes.toDoListService.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CreateItemRequest(
        @Schema(example = "Add Task")
        String description,

        @Schema(example = "2030-01-01T10:00:00", description = "Must be in the future")
        LocalDateTime dueDateTime) {}
