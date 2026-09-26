package com.tradeBytes.toDoLIstService.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "to_do_item")
public class ToDoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String description;

    @Convert(converter = StatusConverter.class)
    private Status status;

    @Column(name = "creation_datetime", nullable = false)
    private LocalDateTime creationDateTime;

    @Column(name = "due_datetime", nullable = false)
    private LocalDateTime dueDateTime;

    @Column(name = "done_datetime")
    private LocalDateTime doneDateTime;



}
