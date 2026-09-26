package com.tradeBytes.toDoLIstService.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Status} in the database using its string value ("not done", "done", "past due").
 */
@Converter
public class StatusConverter implements AttributeConverter<Status, String> {

    @Override
    public String convertToDatabaseColumn(Status status) {

        return status == null ? null : status.getValue();
    }

    @Override
    public Status convertToEntityAttribute(String value) {

        return value == null ? null : Status.fromValue(value);
    }
}
