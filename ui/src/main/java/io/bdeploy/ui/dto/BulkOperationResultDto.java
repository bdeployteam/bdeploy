package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Returned by bulk operations which allow individual operations to provide feedback.
 */
public class BulkOperationResultDto {

    /**
     * The results provided.
     */
    public List<OperationResult> results = new ArrayList<>();

    public synchronized void add(OperationResult result) {
        results.add(result);
    }
}
