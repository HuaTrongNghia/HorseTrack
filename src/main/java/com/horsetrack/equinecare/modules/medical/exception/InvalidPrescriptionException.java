package com.horsetrack.equinecare.modules.medical.exception;
public class InvalidPrescriptionException extends RuntimeException {
    public InvalidPrescriptionException(String message) {
        super(message);
    }
}
