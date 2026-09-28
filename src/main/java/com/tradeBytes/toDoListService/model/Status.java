package com.tradeBytes.toDoListService.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {

    NOT_DONE("not done"),
    DONE("done"),
    PAST_DUE("past due");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    /** Serialised to/from JSON as "not done", "done" or "past due". */
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Status fromValue(String value) {
        for (Status s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }

}
