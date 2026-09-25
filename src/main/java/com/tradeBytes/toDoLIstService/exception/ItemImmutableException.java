package com.tradeBytes.toDoLIstService.exception;

import java.time.LocalDateTime;
import java.util.UUID;

public class ItemImmutableException extends RuntimeException {

    public ItemImmutableException(UUID id, LocalDateTime dueDateTime) {
        super("To-do item " + id + " is past due (due date was " + dueDateTime
                + ") and can no longer be modified.");
    }
}
