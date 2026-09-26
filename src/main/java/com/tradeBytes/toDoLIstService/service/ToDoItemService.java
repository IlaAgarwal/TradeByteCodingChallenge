package com.tradeBytes.toDoLIstService.service;

import com.tradeBytes.toDoLIstService.dto.CreateItemRequest;
import com.tradeBytes.toDoLIstService.exception.ItemImmutableException;
import com.tradeBytes.toDoLIstService.exception.ItemNotFoundException;
import com.tradeBytes.toDoLIstService.model.Status;
import com.tradeBytes.toDoLIstService.model.ToDoItem;
import com.tradeBytes.toDoLIstService.repository.ToDoItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ToDoItemService {

    private final ToDoItemRepository toDoItemRepository;

    public ToDoItemService(ToDoItemRepository ToDoItemRepository) {
        this.toDoItemRepository = ToDoItemRepository;
    }

    public ToDoItem updateStatus(UUID id, Status status) {
        ToDoItem toDoItem = toDoItemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));

        verifyMutable(toDoItem);

        if (toDoItem.getStatus() != status) {
            toDoItem.setStatus(status);
            toDoItem.setDoneDateTime(status == Status.DONE ? LocalDateTime.now() : null);
        }

        return toDoItemRepository.save(toDoItem);
    }

    public List<ToDoItem> getItems(boolean fetchAll) {
        if (fetchAll) {
            return toDoItemRepository.findAll();
        }
        return toDoItemRepository.findByStatus(Status.NOT_DONE);
    }

    public ToDoItem getItem(UUID id) {
        return toDoItemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
    }


    public ToDoItem addItem(CreateItemRequest request) {
        ToDoItem item = new ToDoItem();
        if(request.description().trim().isBlank()) {
            throw new IllegalArgumentException("Description must not be empty.");
        }
        item.setDescription(request.description().trim());
        if(request.dueDateTime() == null) {
            throw new IllegalArgumentException("Due date must not be empty.");
        }
        item.setDueDateTime(request.dueDateTime());
        item.setStatus(Status.NOT_DONE);
        item.setCreationDateTime(LocalDateTime.now());
        return toDoItemRepository.save(item);
    }

    public ToDoItem updateDescription(UUID id, String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be empty.");
        }

        ToDoItem toDoItem = toDoItemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));

        verifyMutable(toDoItem);

        toDoItem.setDescription(description);
        return toDoItemRepository.save(toDoItem);
    }

    /**
     * Moves every "not done" item whose due date has passed to "past due".
     * Called periodically by the scheduler.
     *
     * @return number of items updated
     */
    @Transactional
    public int markPastDueItems() {
        return toDoItemRepository.updateStatusForPastDueItems(Status.PAST_DUE, Status.NOT_DONE, LocalDateTime.now());
    }

    public boolean isImmutable(ToDoItem toDoItem) {
        return toDoItem.getStatus() == Status.PAST_DUE
                || (toDoItem.getStatus() == Status.NOT_DONE
                    && toDoItem.getDueDateTime() != null
                    && toDoItem.getDueDateTime().isBefore(LocalDateTime.now()));
    }

    public void verifyMutable(ToDoItem toDoItem) {
        if (isImmutable(toDoItem)) {
            throw new ItemImmutableException(toDoItem.getId(), toDoItem.getDueDateTime());
        }
    }
}
