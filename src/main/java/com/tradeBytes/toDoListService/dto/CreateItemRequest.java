package com.tradeBytes.toDoListService.dto;

import java.time.LocalDateTime;

public record CreateItemRequest(String description, LocalDateTime dueDateTime) {}