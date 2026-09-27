package com.tradeBytes.toDoListService.repository;

import com.tradeBytes.toDoListService.model.Status;
import com.tradeBytes.toDoListService.model.ToDoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToDoItemRepository extends JpaRepository<ToDoItem, UUID> {

    List<ToDoItem> findByStatusAndDueDateTimeAfter(Status status, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ToDoItem t SET t.status = :newStatus , t.version = t.version + 1 WHERE t.status = :currentStatus AND t.dueDateTime < :now")
    int updateStatusForPastDueItems(@Param("newStatus") Status newStatus,
                                    @Param("currentStatus") Status currentStatus,
                                    @Param("now") LocalDateTime now);


}
