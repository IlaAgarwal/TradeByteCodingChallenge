package com.tradeBytes.toDoLIstService.service;

import com.tradeBytes.toDoLIstService.exception.ItemImmutableException;
import com.tradeBytes.toDoLIstService.model.Status;
import com.tradeBytes.toDoLIstService.model.ToDoItem;
import com.tradeBytes.toDoLIstService.repository.ToDoItemRepository;
import org.springframework.stereotype.Service;

@Service
public class ToDoItemService {

    private final ToDoItemRepository toDoItemRepository;

    public ToDoItemService(ToDoItemRepository toDoItemRepository) {
        this.toDoItemRepository = toDoItemRepository;
    }

    public void updateItem(ToDoItem toDoItem) {
        toDoItemRepository.save(toDoItem);
    }

    public void updateDescription(ToDoItem toDoItem) {
        toDoItemRepository.save(toDoItem);
    }

    public void updateStatus(Status status) {
    }

    public boolean isImmutable(ToDoItem toDoItem) {
        return toDoItem.getStatus() == Status.PAST_DUE;
    }

    public void verifyMutable(ToDoItem toDoItem) {
        if (isImmutable(toDoItem)) {
            throw new ItemImmutableException(toDoItem.getId(), toDoItem.getDueDateTime());
        }
    }
}
