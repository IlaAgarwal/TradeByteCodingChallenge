package com.tradeBytes.toDoLIstService.repository;

import com.tradeBytes.toDoLIstService.model.Status;
import com.tradeBytes.toDoLIstService.model.ToDoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToDoItemRepository extends JpaRepository<ToDoItem, UUID> {

    List<ToDoItem> findByStatus(Status status);

    List<ToDoItem> findByStatusAndDueDateTimeBefore(Status status, LocalDateTime dateTime);

    Optional<ToDoItem> findById(UUID id);




}
