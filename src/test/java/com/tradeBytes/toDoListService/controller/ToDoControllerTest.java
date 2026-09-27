package com.tradeBytes.toDoListService.controller;

import com.tradeBytes.toDoListService.dto.CreateItemRequest;
import com.tradeBytes.toDoListService.exception.ItemImmutableException;
import com.tradeBytes.toDoListService.exception.ItemNotFoundException;
import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.service.ToDoService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP layer only: routing, JSON binding and exception -> status code mapping. Service is mocked. */
@WebMvcTest(ToDoController.class)
class ToDoControllerTest {

    private static final String BASE = "/api/toDoItems";
    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToDoService service;

    private static ToDoItem item(Status status) {
        ToDoItem item = new ToDoItem();
        item.setId(ID);
        item.setDescription("read docs");
        item.setStatus(status);
        item.setCreationDateTime(LocalDateTime.now());
        item.setDueDateTime(LocalDateTime.now().plusDays(1));
        return item;
    }

    @Nested
    class AddItem {

        @Test
        void returns200WithCreatedItem() throws Exception {
            when(service.addItem(any(CreateItemRequest.class))).thenReturn(item(Status.NOT_DONE));

            mockMvc.perform(post(BASE + "/addItem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description": "read docs", "dueDateTime": "2099-01-01T10:00:00"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.status").value("NOT_DONE"))
                    .andExpect(jsonPath("$.version").doesNotExist());
        }

        @Test
        void returns400WhenServiceRejectsInput() throws Exception {
            when(service.addItem(any(CreateItemRequest.class)))
                    .thenThrow(new IllegalArgumentException("Due date must be in the future."));

            mockMvc.perform(post(BASE + "/addItem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description": "read docs", "dueDateTime": "2000-01-01T10:00:00"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Due date must be in the future."))
                    .andExpect(jsonPath("$.path").value(BASE + "/addItem"));
        }

        @Test
        void returns400ForMalformedJson() throws Exception {
            mockMvc.perform(post(BASE + "/addItem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).addItem(any());
        }
    }

    @Nested
    class MarkDone {

        @Test
        void returns200() throws Exception {
            when(service.updateStatus(ID, Status.DONE)).thenReturn(item(Status.DONE));

            mockMvc.perform(patch(BASE + "/{id}/MarkDone", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"));
        }

        @Test
        void returns404WhenItemDoesNotExist() throws Exception {
            when(service.updateStatus(ID, Status.DONE)).thenThrow(new ItemNotFoundException(ID));

            mockMvc.perform(patch(BASE + "/{id}/MarkDone", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void returns409WhenItemIsImmutable() throws Exception {
            when(service.updateStatus(ID, Status.DONE))
                    .thenThrow(new ItemImmutableException(ID, LocalDateTime.now().minusDays(1)));

            mockMvc.perform(patch(BASE + "/{id}/MarkDone", ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void returns409OnConcurrentModification() throws Exception {
            when(service.updateStatus(ID, Status.DONE))
                    .thenThrow(new ObjectOptimisticLockingFailureException(ToDoItem.class, ID));

            mockMvc.perform(patch(BASE + "/{id}/MarkDone", ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Item was changed by someone else, please retry"));
        }

        @Test
        void returns400ForInvalidUuid() throws Exception {
            mockMvc.perform(patch(BASE + "/{id}/MarkDone", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid value 'not-a-uuid' for parameter 'id'."));

            verify(service, never()).updateStatus(any(), any());
        }
    }

    @Test
    void markNotDoneReturns200() throws Exception {
        when(service.updateStatus(ID, Status.NOT_DONE)).thenReturn(item(Status.NOT_DONE));

        mockMvc.perform(patch(BASE + "/{id}/MarkNotdone", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_DONE"));
    }

    @Nested
    class UpdateDescription {

        @Test
        void returns200() throws Exception {
            ToDoItem updated = item(Status.NOT_DONE);
            updated.setDescription("check email");
            when(service.updateDescription(ID, "check email")).thenReturn(updated);

            mockMvc.perform(patch(BASE + "/{id}/updateDescription", ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description": "check email"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("check email"));
        }

        @Test
        void returns400ForBlankDescription() throws Exception {
            when(service.updateDescription(eq(ID), anyString()))
                    .thenThrow(new IllegalArgumentException("Description must be sent."));

            mockMvc.perform(patch(BASE + "/{id}/updateDescription", ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description": "  "}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns409WhenItemIsImmutable() throws Exception {
            when(service.updateDescription(eq(ID), anyString()))
                    .thenThrow(new ItemImmutableException(ID, LocalDateTime.now().minusDays(1)));

            mockMvc.perform(patch(BASE + "/{id}/updateDescription", ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description": "check email"}
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class GetItems {

        @Test
        void defaultsToFetchAllFalse() throws Exception {
            when(service.getItems(false)).thenReturn(List.of(item(Status.NOT_DONE)));

            mockMvc.perform(get(BASE + "/getAllItems"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(service).getItems(false);
        }

        @Test
        void passesFetchAllTrue() throws Exception {
            when(service.getItems(true)).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/getAllItems").param("fetchAll", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(service).getItems(true);
        }

        @Test
        void returns400ForNonBooleanFetchAll() throws Exception {
            mockMvc.perform(get(BASE + "/getAllItems").param("fetchAll", "maybe"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class GetItem {

        @Test
        void returns200WithSingleItemList() throws Exception {
            when(service.getItem(ID)).thenReturn(item(Status.NOT_DONE));

            mockMvc.perform(get(BASE + "/{id}/getItem", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ID.toString()));
        }

        @Test
        void returns404WhenItemDoesNotExist() throws Exception {
            when(service.getItem(ID)).thenThrow(new ItemNotFoundException(ID));

            mockMvc.perform(get(BASE + "/{id}/getItem", ID))
                    .andExpect(status().isNotFound());
        }
    }
}
