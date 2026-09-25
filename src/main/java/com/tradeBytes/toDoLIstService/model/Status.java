package com.tradeBytes.toDoLIstService.model;

import lombok.Getter;

@Getter
public enum Status {

    NOT_DONE("not done"),
    DONE("done"),
    PAST_DUE("past due");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    public static Status fromValue(String value) {
        for (Status s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
