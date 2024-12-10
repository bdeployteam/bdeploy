package io.bdeploy.bhive.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTestUtils;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.util.FutureHelper;

@ExtendWith(TestHive.class)
class ManifestAddDeleteLoadTest {

    private static final Logger log = LoggerFactory.getLogger(ManifestAddDeleteLoadTest.class);

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 4 })
    void testParallelDelete(int factor, BHive hive) {

        // 1. create a ton of manifests.
        List<Manifest.Key> manifests = new ArrayList<>();

        log.info("Producing test data, {} manifests.", 1_000 * factor);
        for (int i = 0; i < (100 * factor); ++i) {
            manifests.add(BHiveTestUtils.createManifest(hive, "test_" + i, true));
        }

        // 2. lookup and delete them with multiple threads in parallel
        int noGroups = 4;
        int groupSize = (manifests.size() / noGroups);
        List<List<Manifest.Key>> groups = IntStream.range(0, noGroups)
                .mapToObj(i -> manifests.subList(groupSize * i, Math.min(groupSize * i + groupSize, manifests.size())))
                .collect(Collectors.toList());

        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(groupSize);
        for (List<Manifest.Key> group : groups) {
            futures.add(executor.submit(() -> delete(hive, group)));
        }

        // 3. wait for all, which should not throw...
        FutureHelper.awaitAll(futures);
    }

    private static void delete(BHive hive, List<Manifest.Key> keys) {
        log.info("Deleting {} manifests", keys.size());

        for (Manifest.Key k : keys) {
            // 1. list manifest.
            Set<Manifest.Key> mf = hive.execute(new ManifestListOperation().setManifestName(k.getName()));

            assertNotNull(mf);
            assertEquals(1, mf.size());
            assertEquals(k, mf.iterator().next());

            // 2. try to list *all* manifests, which will result in an overlap in reading and deleting with other threads.
            Set<Manifest.Key> allMfs = hive.execute(new ManifestListOperation());
            allMfs.forEach(m -> hive.execute(new ManifestLoadOperation().setNullOnError(true).setManifest(m)));

            assertTrue(allMfs.contains(k));

            // 3. delete
            hive.execute(new ManifestDeleteOperation().setToDelete(k));
        }
    }

}
