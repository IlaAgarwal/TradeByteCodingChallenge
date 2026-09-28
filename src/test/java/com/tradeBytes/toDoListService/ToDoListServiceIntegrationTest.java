package com.tradeBytes.toDoListService;

import com.jayway.jsonpath.JsonPath;
import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import com.tradeBytes.toDoListService.repository.ToDoItemRepository;
import com.tradeBytes.toDoListService.scheduler.PastDueStatusScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application context with the real H2 database; the scheduler is triggered manually. */
@SpringBootTest(properties = "todo.past-due-check.interval=1h")
@AutoConfigureMockMvc
class ToDoListServiceIntegrationTest {

    private static final String BASE = "/api/toDoItems";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToDoItemRepository repo;

    @Autowired
    private PastDueStatusScheduler scheduler;

    @BeforeEach
    void cleanDatabase() {
        repo.deleteAll();
    }

    @Test
    void itemLifecycleThroughTheApi() throws Exception {
        String body = mockMvc.perform(post(BASE + "/addItem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "  prepare daily report  ", "dueDateTime": "2099-01-01T10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("prepare daily report"))
                .andExpect(jsonPath("$.status").value("not done"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(patch(BASE + "/{id}/MarkDone", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.doneDateTime").isNotEmpty());

        mockMvc.perform(patch(BASE + "/{id}/MarkNotdone", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("not done"))
                .andExpect(jsonPath("$.doneDateTime").isEmpty());

        mockMvc.perform(patch(BASE + "/{id}/updateDescription", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "send daily report"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{id}/getItem", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("send daily report"))
                .andExpect(jsonPath("$[0].status").value("not done"));

        mockMvc.perform(get(BASE + "/getAllItems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(repo.findById(UUID.fromString(id))).get()
                .extracting(ToDoItem::getDescription).isEqualTo("send daily report");
    }

    @Test
    void schedulerFlagsOverdueItemsWhichThenRejectChanges() throws Exception {
        // The API refuses past due dates, so seed an overdue item directly
        ToDoItem overdue = new ToDoItem();
        overdue.setDescription("update tech spec");
        overdue.setStatus(Status.NOT_DONE);
        overdue.setCreationDateTime(LocalDateTime.now().minusDays(2));
        overdue.setDueDateTime(LocalDateTime.now().minusDays(1));
        UUID id = repo.save(overdue).getId();

        scheduler.markPastDueItems();

        mockMvc.perform(get(BASE + "/getAllItems").param("fetchAll", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("past due"));

        mockMvc.perform(get(BASE + "/getAllItems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch(BASE + "/{id}/MarkDone", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(repo.findById(id)).get()
                .extracting(ToDoItem::getStatus).isEqualTo(Status.PAST_DUE);
    }
}
