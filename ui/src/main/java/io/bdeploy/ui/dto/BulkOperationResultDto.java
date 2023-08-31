package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Returned by bulk operations which allow individual operations to provide feedback.
 */
public class BulkOperationResultDto {

    public enum OperationResultType {
        INFO,
        WARNING,
        ERROR
    }

    public record OperationResult(String target, OperationResultType type, String message) {
    }

    /**
     * The results provided.
     */
    public List<OperationResult> results = new ArrayList<>();

    public synchronized void add(OperationResult result) {
        results.add(result);
    }

}
