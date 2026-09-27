package com.tradeBytes.toDoListService;

import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.repository.ToDoItemRepository;
import com.tradeBytes.toDoListService.service.ToDoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real H2 database with real transactions (no @Transactional on the class),
 * so commits, version checks and row locks behave as in production.
 * The scheduler interval is set high so it never fires on its own during a test.
 */
@SpringBootTest(properties = "todo.past-due-check.interval=1h")
class ToDoConcurrencyTest {

    @Autowired
    ToDoService service;
    @Autowired
    ToDoItemRepository repo;
    @Autowired
    PlatformTransactionManager txManager;

    @BeforeEach
    void cleanDatabase() {
        repo.deleteAll();
    }

    /** Stores an item in the given state; returns its id. */
    private UUID givenItem(String description, Status status, LocalDateTime dueDateTime) {
        ToDoItem item = new ToDoItem();
        item.setDescription(description);
        item.setStatus(status);
        item.setDueDateTime(dueDateTime);
        item.setCreationDateTime(LocalDateTime.now());
        return repo.save(item).getId();
    }

    /** Reads the committed state of the item from the database. */
    private ToDoItem stored(UUID id) {
        return repo.findById(id).orElseThrow();
    }

    /** Runs all tasks at the same moment on separate threads and waits for them to finish. */
    private <T> List<Future<T>> runSimultaneously(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void schedulerWins_whenItemBecomesPastDueBetweenRequestCheckAndCommit() {
        // Given: an item due tomorrow, so the request's own check passes
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        UUID id = givenItem("submit report", Status.NOT_DONE, tomorrow);

        TransactionTemplate requestTx = new TransactionTemplate(txManager);
        TransactionTemplate schedulerTx = new TransactionTemplate(txManager);
        schedulerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // When: the request passes its check, then the scheduler commits before the request does
        assertThatThrownBy(() -> requestTx.executeWithoutResult(status -> {
            service.updateStatus(id, Status.DONE);          // check passes, change not committed yet

            schedulerTx.executeWithoutResult(s ->           // separate transaction, commits now
                    repo.updateStatusForPastDueItems(
                            Status.PAST_DUE, Status.NOT_DONE, tomorrow.plusDays(1)));
        }))                                                 // request commits here → version mismatch
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Then: the scheduler's change won
        ToDoItem item = stored(id);
        assertThat(item.getStatus()).isEqualTo(Status.PAST_DUE);
        assertThat(item.getDoneDateTime()).isNull();
    }

    @Test
    void secondWriterFails_whenTwoRequestsEditTheSameItem() {
        // Given
        UUID id = givenItem("draft", Status.NOT_DONE, LocalDateTime.now().plusDays(1));

        TransactionTemplate firstRequestTx = new TransactionTemplate(txManager);
        TransactionTemplate secondRequestTx = new TransactionTemplate(txManager);
        secondRequestTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // When: both requests read version 0; the second one commits first
        assertThatThrownBy(() -> firstRequestTx.executeWithoutResult(status -> {
            service.updateDescription(id, "first edit");    // not committed yet

            secondRequestTx.executeWithoutResult(s ->       // commits now, version 0 → 1
                    service.updateDescription(id, "second edit"));
        }))                                                 // first commits with stale version 0
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Then: no lost update – the committed edit survives, and only one version bump happened
        ToDoItem item = stored(id);
        assertThat(item.getDescription()).isEqualTo("second edit");
        assertThat(item.getVersion()).isEqualTo(1L);
    }

    @Test
    void parallelEdits_everySuccessfulCommitIsCountedAndLosersGetOptimisticLockError() throws Exception {
        // Given
        UUID id = givenItem("initial", Status.NOT_DONE, LocalDateTime.now().plusDays(1));
        int threads = 8;

        AtomicInteger conflicts = new AtomicInteger();
        Set<String> committedDescriptions = ConcurrentHashMap.newKeySet();
        List<Callable<Void>> edits = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String description = "edit-" + i;
            edits.add(() -> {
                try {
                    service.updateDescription(id, description);
                    committedDescriptions.add(description);
                } catch (OptimisticLockingFailureException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            });
        }

        // When: all threads edit the same item at the same moment
        for (Future<Void> f : runSimultaneously(edits)) {
            f.get();                                        // rethrows anything unexpected
        }

        // Then: each thread either committed or got a conflict; the version reflects every commit
        ToDoItem item = stored(id);
        assertThat(committedDescriptions).isNotEmpty();
        assertThat(committedDescriptions.size() + conflicts.get()).isEqualTo(threads);
        assertThat(item.getVersion()).isEqualTo((long) committedDescriptions.size());
        assertThat(item.getDescription()).isIn(committedDescriptions);
    }
}
