package com.tradeBytes.toDoListService.scheduler;

import com.tradeBytes.toDoListService.service.ToDoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flags "not done" items whose due date has passed as "past due".
 * Runs once at startup and then at the interval configured by
 * {@code todo.past-due-check.interval} (default: every minute).
 */
@Component
public class PastDueStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(PastDueStatusScheduler.class);

    private final ToDoService toDoService;

    public PastDueStatusScheduler(ToDoService toDoService) {
        this.toDoService = toDoService;
    }

    @Scheduled(fixedDelayString = "${todo.past-due-check.interval}")
    public void markPastDueItems() {
        int updated = toDoService.markPastDueItems();
        if (updated > 0) {
            log.info("Marked {} item(s) as past due", updated);
        }
    }
}
