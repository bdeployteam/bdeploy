package io.bdeploy.bhive.op;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestConsistencyCheckOperation;
import io.bdeploy.bhive.op.ObjectConsistencyCheckOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class FsckOpTest {

    @Test
    public void testFsck(BHive hive, @TempDir Path tmp) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "src");
        Manifest.Key key = new Manifest.Key("test", "v1");

        assertThat(hive.execute(new ImportOperation().setManifest(key).setSourcePath(src)), is(key));

        Path fileToMessWith = hive.execute(new BHive.Operation<Path>() {

            @Override
            public Path call() throws Exception {
                return getObjectManager().db(x -> {
                    return x.getObjectFile(ObjectId.parse(ContentHelper.TEST_TXT_OID));
                });
            }
        });

        Files.write(fileToMessWith, Collections.singleton("This is something broken"));

        List<ElementView> broken = hive.execute(new ObjectConsistencyCheckOperation().setDryRun(false).addRoot(key));
        assertThat(broken.size(), is(1));
        assertThat(broken.get(0).getElementId(), is(ObjectId.parse(ContentHelper.TEST_TXT_OID)));
        assertFalse(Files.exists(fileToMessWith));

        // the previous non-dry-run check removed the broken object. now is is no longer found by the object check
        broken = hive.execute(new ObjectConsistencyCheckOperation().addRoot(key));
        assertThat(broken.size(), is(0));

        // but the manifest consistency check will find a missing object now.
        broken = hive.execute(new ManifestConsistencyCheckOperation().addRoot(key));
        assertThat(broken.size(), is(1));
        assertThat(broken.get(0).getElementId(), is(ObjectId.parse(ContentHelper.TEST_TXT_OID)));
    }

}
