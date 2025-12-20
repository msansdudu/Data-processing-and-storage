package ru.nsu.chebotareva.model;

import java.util.List;

public class ResponseDto {
    private String message;
    private List<String> successors;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getSuccessors() {
        return successors;
    }

    public void setSuccessors(List<String> successors) {
        this.successors = successors;
    }
}
