package io.bdeploy.interfaces;

import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MISSING;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MODIFIED;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.UNMODIFIED;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.op.VerifyOperation;
import io.bdeploy.bhive.op.VerifyOperation.VerifiedBlobView;
import io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus;

public class VerifyOperationResultDto {

    public List<String> missingFiles = new ArrayList<>();
    public List<String> modifiedFiles = new ArrayList<>();
    public List<String> unmodifiedFiles = new ArrayList<>();

    public VerifyOperationResultDto() {
    }

    public VerifyOperationResultDto(List<VerifyOperation.VerifiedBlobView> list) {
        missingFiles = getByStatus(list, MISSING);
        modifiedFiles = getByStatus(list, MODIFIED);
        unmodifiedFiles = getByStatus(list, UNMODIFIED);
    }

    private List<String> getByStatus(List<VerifiedBlobView> list, VerifyOpStatus status) {
        return list.stream().filter(view -> view.status == status).map(blob -> blob.path).toList();
    }
}
