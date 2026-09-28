package com.tradeBytes.toDoListService.service;

import com.tradeBytes.toDoListService.dto.CreateItemRequest;
import com.tradeBytes.toDoListService.exception.ItemImmutableException;
import com.tradeBytes.toDoListService.exception.ItemNotFoundException;
import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.repository.ToDoItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ToDoService {

    private static final Logger log = LoggerFactory.getLogger(ToDoService.class);

    private final ToDoItemRepository toDoItemRepository;

    public ToDoService(ToDoItemRepository toDoItemRepository) {
        this.toDoItemRepository = toDoItemRepository;
    }

    @Transactional
    public ToDoItem updateStatus(UUID id, Status status) {
        ToDoItem toDoItem = getItem(id);

        verifyMutable(toDoItem);

        if (toDoItem.getStatus() != status) {
            log.info("Item {} status changed {} -> {}", id, toDoItem.getStatus(), status);
            toDoItem.setStatus(status);
            toDoItem.setDoneDateTime(status == Status.DONE ? LocalDateTime.now() : null);
        } else {
            log.debug("Item {} already has status {}, nothing to change", id, status);
        }

        return toDoItemRepository.save(toDoItem);
    }

    public List<ToDoItem> getItems(boolean fetchAll) {
        List<ToDoItem> items = fetchAll
                ? toDoItemRepository.findAll()
                : toDoItemRepository.findByStatusAndDueDateTimeAfter(Status.NOT_DONE, LocalDateTime.now());
        log.debug("Fetched {} item(s), fetchAll={}", items.size(), fetchAll);
        return items;
    }

    public ToDoItem getItem(UUID id) {
        return toDoItemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
    }


    public ToDoItem addItem(CreateItemRequest request) {
        ToDoItem item = new ToDoItem();
        if(null==request.description() || request.description().isBlank()) {
            throw new IllegalArgumentException("Description must not be empty.");
        }
        else {
            item.setDescription(request.description().trim());
        }
        if(request.dueDateTime() == null) {
            throw new IllegalArgumentException("Due date must not be empty.");
        }
        if(request.dueDateTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Due date must be in the future.");
        }
        item.setDueDateTime(request.dueDateTime());
        item.setStatus(Status.NOT_DONE);
        item.setCreationDateTime(LocalDateTime.now());
        ToDoItem saved = toDoItemRepository.save(item);
        log.info("Created item {} due {}", saved.getId(), saved.getDueDateTime());
        return saved;
    }

    @Transactional
    public ToDoItem updateDescription(UUID id, String description) {
        if (null==description || description.isBlank()) {
            throw new IllegalArgumentException("Description must be sent.");
        }

        ToDoItem toDoItem = toDoItemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));

        verifyMutable(toDoItem);

        toDoItem.setDescription(description.trim());
        log.info("Item {} description updated", id);
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
