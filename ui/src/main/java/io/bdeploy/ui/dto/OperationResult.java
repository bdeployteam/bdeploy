package io.bdeploy.ui.dto;

public record OperationResult(String target, OperationResultType type, String message) {

    public enum OperationResultType {
        INFO,
        WARNING,
        ERROR
    }
}
