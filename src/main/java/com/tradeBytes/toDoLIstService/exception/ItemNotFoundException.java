package com.tradeBytes.toDoLIstService.exception;

import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(UUID id) {
        super("To-do item " + id + " was not found.");
    }
}
