package io.bdeploy.ui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.jvnet.hk2.annotations.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;

@Service
public class ProductDiscUsageService {

    @Inject
    BHiveRegistry registry;

    private final Cache<String, Long> cachedUsage = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Map<String, CompletableFuture<Long>> calculations = new ConcurrentSkipListMap<>();

    public CompletableFuture<Long> getDiscUsage(String group, String mfName) {
        var key = group + "_" + mfName;
        var cached = cachedUsage.getIfPresent(key);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        var stage = calculations.get(key);
        if (stage != null) {
            return stage;
        }

        var future = CompletableFuture.supplyAsync(() -> doCalculateUsage(group, mfName));
        calculations.put(key, future);
        return future.whenComplete((r, t) -> {
            if (r != null) {
                cachedUsage.put(key, r);
            }
            calculations.remove(key);
        });
    }

    public void invalidateDiscUsageCalculation(String group, String name) {
        var key = group + "_" + name;
        var future = calculations.get(key);

        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        cachedUsage.invalidate(key);
    }

    public void invalidateDiscUsageCalculation(String group) {
        var prefix = group + "_";
        calculations.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)).forEach(e -> e.getValue().cancel(true));
        cachedUsage.asMap().keySet().stream().filter(k -> k.startsWith(prefix)).forEach(cachedUsage::invalidate);
    }

    private Long doCalculateUsage(String group, String name) {
        BHive hive = registry.get(group);
        Set<Key> mfs = hive.execute(new ManifestListOperation().setManifestName(name));
        Set<ObjectId> objs = hive.execute(new ObjectListOperation().addManifest(mfs));
        return hive.execute(new ObjectSizeOperation().addObject(objs));
    }

}
