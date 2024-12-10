package io.bdeploy.bhive.op;

import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MISSING;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.MODIFIED;
import static io.bdeploy.bhive.op.VerifyOperation.VerifyOpStatus.UNMODIFIED;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.VerifyOperation.VerifiedBlobView;

/**
 * Scan a given target folder and origin {@link Manifest} and identify which files were deleted or modified.
 */
@ReadOnlyOperation
public class VerifyOperation extends BHive.Operation<List<VerifiedBlobView>> {

    private Path targetPath;
    private Manifest.Key manifest;

    @Override
    public List<VerifiedBlobView> call() {
        List<VerifiedBlobView> result = new ArrayList<>();
        TreeView state = execute(new ScanOperation().setManifest(manifest));
        state.visit(new TreeVisitor.Builder().onBlob(blob -> this.visit(blob, result)).build());
        return result;
    }

    private void visit(BlobView blob, List<VerifiedBlobView> result) {
        Path path = targetPath.resolve(blob.getPathString());
        if (!path.toFile().exists()) {
            result.add(new VerifiedBlobView(MISSING, blob.getPathString()));
            return;
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(path.toFile()))) {
            ObjectId objectId = ObjectId.createFromStreamNoCopy(is);
            if (objectId.equals(blob.getElementId())) {
                result.add(new VerifiedBlobView(UNMODIFIED, blob.getPathString()));
            } else {
                result.add(new VerifiedBlobView(MODIFIED, blob.getPathString()));
            }
        } catch (Exception e) {
            result.add(new VerifiedBlobView(MODIFIED, blob.getPathString()));
        }
    }

    /**
     * Set the target path to check files for. The given directory will be compared against scanned {@link Manifest}
     */
    public VerifyOperation setTargetPath(Path targetPath) {
        this.targetPath = targetPath;
        return this;
    }

    /**
     * Set the {@link Manifest} to scan. Scans the {@link Manifest}s root tree.
     */
    public VerifyOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

    public static class VerifiedBlobView {

        public final VerifyOpStatus status;
        public final String path;

        private VerifiedBlobView(VerifyOpStatus status, String path) {
            this.status = status;
            this.path = path;
        }
    }

    public enum VerifyOpStatus {
        MISSING,
        MODIFIED,
        UNMODIFIED
    }
}
