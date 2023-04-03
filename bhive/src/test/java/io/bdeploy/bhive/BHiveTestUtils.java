package io.bdeploy.bhive;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.util.StorageHelper;

public class BHiveTestUtils {

    private static final Logger log = LoggerFactory.getLogger(BHiveTestUtils.class);
    private static final SecureRandom r = new SecureRandom();

    private BHiveTestUtils() {
    }

    public static Manifest.Key createManifest(BHive hive, String manifestName, boolean silent) {
        long start = System.currentTimeMillis();
        String content = "{" + start + "_" + r.nextInt() + "}";

        Optional<Long> currentId = hive.execute(new ManifestMaxIdOperation().setManifestName(manifestName));
        if (currentId.isPresent()) {
            hive.execute(new FsckOperation().addManifest(new Manifest.Key(manifestName, currentId.get().toString())));
        }

        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(manifestName));
        Manifest.Key key = new Manifest.Key(manifestName, newId.toString());
        Manifest.Builder mfb = new Manifest.Builder(key);

        List<ObjectId> oids = new ArrayList<>();
        try (Transaction t = hive.getTransactions().begin()) {
            ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(content)));
            Tree.Builder tb = new Tree.Builder().add(new Tree.Key("file.x", Tree.EntryType.BLOB), descOid);
            ObjectId treeOid = hive.execute(new InsertArtificialTreeOperation().setTree(tb));
            mfb.setRoot(treeOid);

            oids.add(descOid);
            oids.add(treeOid);

            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
        }
        if (!silent) {
            log.info("Producer: {} {} took {}ms.", newId, oids, System.currentTimeMillis() - start);
        }

        return key;
    }

}
