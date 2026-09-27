package com.tradeBytes.toDoListService.service;

import com.tradeBytes.toDoListService.dto.CreateItemRequest;
import com.tradeBytes.toDoListService.exception.ItemImmutableException;
import com.tradeBytes.toDoListService.exception.ItemNotFoundException;
import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.repository.ToDoItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TodoServiceTest {

    private static final LocalDateTime TOMORROW  = LocalDateTime.now().plusDays(1);
    private static final LocalDateTime YESTERDAY = LocalDateTime.now().minusDays(1);

    private final Map<UUID, ToDoItem> toDoItems = new HashMap<>();
    private final ToDoItemRepository repo = mock(ToDoItemRepository.class);
    private final ToDoService service = new ToDoService(repo);

    @BeforeEach
    void stubRepository() {
        when(repo.save(any(ToDoItem.class))).thenAnswer(invocation -> {
            ToDoItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            toDoItems.put(item.getId(), item);
            return item;
        });
        when(repo.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(toDoItems.get(invocation.<UUID>getArgument(0))));
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

    private ToDoItem stored(UUID id) { return toDoItems.get(id); }

    @Nested
    class WhenAddItems {
        @Test
        void startsAsNotDoneWithoutDoneDate() {
            ToDoItem created = service.addItem(new CreateItemRequest("prepare daily report", TOMORROW));
            assertThat(stored(created.getId()).getStatus()).isEqualTo(Status.NOT_DONE);
            assertThat(stored(created.getId()).getDoneDateTime()).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        void rejectsMissingDescription(String description) {
            assertThatThrownBy(() -> service.addItem(new CreateItemRequest(description, TOMORROW)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Description must not be empty.");
            verify(repo, never()).save(any());
        }

        @Test
        void rejectsMissingDueDate() {
            assertThatThrownBy(() -> service.addItem(new CreateItemRequest("Upgrade libraries", null)))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void rejectsDueDateInThePast() {
            assertThatThrownBy(() -> service.addItem(new CreateItemRequest("Upgrade libraries", YESTERDAY)))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class WhenNotDoneAndDueInFuture {
        UUID id;

        @BeforeEach
        void setUp() { id = givenItem("read docs", Status.NOT_DONE, TOMORROW); }

        @Test
        void descriptionCanBeUpdated() {
            service.updateDescription(id, "check email");
            assertThat(stored(id).getDescription()).isEqualTo("check email");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        void rejectsMissingDescriptionAndLeavesItemUnchanged(String description) {
            clearInvocations(repo);                         // ignore the save done by setUp

            assertThatThrownBy(() -> service.updateDescription(id, description))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Description must be sent.");
            assertThat(stored(id).getDescription()).isEqualTo("read docs");
            verify(repo, never()).save(any());
        }

        @Test
        void canBeMarkedDone() {
            service.updateStatus(id, Status.DONE);
            assertThat(stored(id).getStatus()).isEqualTo(Status.DONE);
            assertThat(stored(id).getDoneDateTime()).isNotNull();
        }
    }

    @Nested
    class Immutability {

        @Test
        void pastDueStatusIsImmutableEvenWithFutureDueDate() {
            ToDoItem item = new ToDoItem();
            item.setStatus(Status.PAST_DUE);
            item.setDueDateTime(TOMORROW);
            assertThat(service.isImmutable(item)).isTrue();
        }

        @Test
        void itemWithoutDueDateIsMutable() {
            ToDoItem item = new ToDoItem();
            item.setStatus(Status.NOT_DONE);
            assertThat(service.isImmutable(item)).isFalse();
        }
    }

    @Nested
    class WhenNotDoneAndPastDue {
        UUID id;

        @BeforeEach
        void setUp() { id = givenItem("check PR", Status.NOT_DONE, YESTERDAY); }

        @Test
        void rejectsDescriptionUpdateAndLeavesItemUnchanged() {
            assertThatThrownBy(() -> service.updateDescription(id, "review PR"))
                .isInstanceOf(ItemImmutableException.class);
            assertThat(stored(id).getDescription()).isEqualTo("check PR");
        }

        @Test
        void rejectsMarkingDoneAndLeavesItemUnchanged() {
            assertThatThrownBy(() -> service.updateStatus(id, Status.DONE))
                .isInstanceOf(ItemImmutableException.class);
            assertThat(stored(id).getStatus()).isEqualTo(Status.NOT_DONE);
        }
    }

    @Nested
    class WhenDone {

        @Test
        void canBeReopened() {
            UUID id = givenItem("check emails", Status.DONE, TOMORROW);

            service.updateStatus(id, Status.NOT_DONE);

            assertThat(stored(id).getStatus()).isEqualTo(Status.NOT_DONE);
            assertThat(stored(id).getDoneDateTime()).isNull();
        }

        @Test
        void cannotBeReopenedAfterDueDate() {
            UUID id = givenItem("check emails", Status.DONE, YESTERDAY);

            assertThatThrownBy(() -> service.updateStatus(id, Status.NOT_DONE))
                .isInstanceOf(ItemImmutableException.class);
            assertThat(stored(id).getStatus()).isEqualTo(Status.DONE);
        }
    }

    @Nested
    class WhenPastDue {
        UUID id;

        // State the scheduler would have produced
        @BeforeEach
        void setUp() { id = givenItem("update tech spec", Status.PAST_DUE, YESTERDAY); }

        @Test
        void rejectsDescriptionUpdate() {
            assertThatThrownBy(() -> service.updateDescription(id, "review tech spec"))
                    .isInstanceOf(ItemImmutableException.class);
            assertThat(stored(id).getDescription()).isEqualTo("update tech spec");
        }

        @Test
        void rejectsAnyStatusChange() {
            assertThatThrownBy(() -> service.updateStatus(id, Status.DONE))
                    .isInstanceOf(ItemImmutableException.class);
            assertThatThrownBy(() -> service.updateStatus(id, Status.NOT_DONE))
                    .isInstanceOf(ItemImmutableException.class);
            assertThat(stored(id).getStatus()).isEqualTo(Status.PAST_DUE);
        }
    }

    @Test
    void unknownIdThrowsNotFound() {
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(), Status.DONE))
            .isInstanceOf(ItemNotFoundException.class);
    }


}
