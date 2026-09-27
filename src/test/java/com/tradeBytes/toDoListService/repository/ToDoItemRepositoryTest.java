package com.tradeBytes.toDoListService.repository;

import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence layer only: custom queries, the status column mapping and optimistic locking.
 * <p>
 * Uses the pooled application datasource (own in-memory DB) instead of the default embedded one:
 * H2 ties the generated {@code status IN (...)} check constraint to the DDL session, and the
 * non-pooled embedded datasource closes that session, making every insert fail.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:repository-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ToDoItemRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Autowired
    private ToDoItemRepository repo;

    @Autowired
    private TestEntityManager em;

    private UUID persist(String description, Status status, LocalDateTime dueDateTime) {
        ToDoItem item = new ToDoItem();
        item.setDescription(description);
        item.setStatus(status);
        item.setDueDateTime(dueDateTime);
        item.setCreationDateTime(NOW.minusDays(7));
        UUID id = em.persistAndFlush(item).getId();
        em.clear();
        return id;
    }

    @Nested
    class FindByStatusAndDueDateTimeAfter {

        @Test
        void returnsOnlyNotDoneItemsDueInTheFuture() {
            UUID wanted = persist("future not done", Status.NOT_DONE, NOW.plusDays(1));
            persist("overdue not done", Status.NOT_DONE, NOW.minusDays(1));
            persist("future done", Status.DONE, NOW.plusDays(1));
            persist("past due", Status.PAST_DUE, NOW.minusDays(1));

            List<ToDoItem> result = repo.findByStatusAndDueDateTimeAfter(Status.NOT_DONE, NOW);

            assertThat(result).extracting(ToDoItem::getId).containsExactly(wanted);
        }
    }

    @Nested
    class UpdateStatusForPastDueItems {

        @Test
        void flagsOnlyOverdueNotDoneItems() {
            UUID overdue = persist("overdue not done", Status.NOT_DONE, NOW.minusMinutes(1));
            UUID future = persist("future not done", Status.NOT_DONE, NOW.plusDays(1));
            UUID doneOverdue = persist("overdue done", Status.DONE, NOW.minusDays(1));

            int updated = repo.updateStatusForPastDueItems(Status.PAST_DUE, Status.NOT_DONE, NOW);

            assertThat(updated).isEqualTo(1);
            assertThat(em.find(ToDoItem.class, overdue).getStatus()).isEqualTo(Status.PAST_DUE);
            assertThat(em.find(ToDoItem.class, future).getStatus()).isEqualTo(Status.NOT_DONE);
            assertThat(em.find(ToDoItem.class, doneOverdue).getStatus()).isEqualTo(Status.DONE);
        }

        @Test
        void bumpsVersionOfUpdatedRows() {
            UUID overdue = persist("overdue not done", Status.NOT_DONE, NOW.minusMinutes(1));
            long versionBefore = em.find(ToDoItem.class, overdue).getVersion();
            em.clear();

            repo.updateStatusForPastDueItems(Status.PAST_DUE, Status.NOT_DONE, NOW);

            assertThat(em.find(ToDoItem.class, overdue).getVersion()).isEqualTo(versionBefore + 1);
        }

        @Test
        void returnsZeroWhenNothingIsOverdue() {
            persist("future not done", Status.NOT_DONE, NOW.plusDays(1));

            assertThat(repo.updateStatusForPastDueItems(Status.PAST_DUE, Status.NOT_DONE, NOW)).isZero();
        }
    }

    @Test
    void statusIsStoredAsStringValue() {
        UUID id = persist("read docs", Status.PAST_DUE, NOW.minusDays(1));

        Object raw = em.getEntityManager()
                .createNativeQuery("SELECT status FROM to_do_item WHERE id = ?1")
                .setParameter(1, id)
                .getSingleResult();

        assertThat(raw).isEqualTo("past due");
    }

    @Test
    void staleUpdateFailsWithOptimisticLockingException() {
        UUID id = persist("read docs", Status.NOT_DONE, NOW.plusDays(1));

        ToDoItem stale = repo.findById(id).orElseThrow();
        em.detach(stale);

        ToDoItem fresh = repo.findById(id).orElseThrow();
        fresh.setDescription("first writer");
        repo.saveAndFlush(fresh);
        em.clear();

        stale.setDescription("second writer");
        assertThatThrownBy(() -> repo.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
