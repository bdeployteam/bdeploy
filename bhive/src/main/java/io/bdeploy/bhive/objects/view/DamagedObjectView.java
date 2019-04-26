package io.bdeploy.bhive.objects.view;

import java.util.Collection;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ScanOperation;

/**
 * Holds data about an object which was damaged. This usually means that the file's content does not match the expected content
 * checksum when running the {@link FsckOperation}. Can also happen when running the {@link ScanOperation} on broken {@link Tree}s
 * or {@link Manifest} references (i.e. cannot be deserialized).
 * <p>
 * A {@link DamagedObjectView} is also a {@link MissingObjectView} as the object referred to cannot be used.
 */
public class DamagedObjectView extends MissingObjectView {

    public DamagedObjectView(ObjectId id, EntryType type, Collection<String> path) {
        super(id, type, path);
    }

}
