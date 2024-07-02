package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefLoadOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.interfaces.RepairAndPruneResultDto;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.ui.api.HiveLoggingResource;
import io.bdeploy.ui.api.HiveResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.HiveEntryDto;
import io.bdeploy.ui.dto.HiveInfoDto;
import io.bdeploy.ui.dto.HiveInfoDto.HiveType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

public class HiveResourceImpl implements HiveResource {

    private static final Logger log = LoggerFactory.getLogger(HiveResourceImpl.class);

    @Inject
    private Minion minion;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private VersionSorterService vss;

    @Inject
    private ResourceContext rc;

    @Inject
    private ActionFactory af;

    @Override
    public List<HiveInfoDto> listHives() {
        log.debug("listHives()");
        List<HiveInfoDto> result = new ArrayList<>();

        for (var entry : registry.getAll().entrySet()) {
            HiveInfoDto hid = new HiveInfoDto();
            hid.name = entry.getKey();

            BHive hive = entry.getValue();
            hid.canPool = minion.getDefaultPoolPath() != null;
            hid.pooling = hive.isPooling();
            Path pool = hive.getPoolPath();
            hid.poolPath = pool != null ? pool.toString() : null;
            hid.minPermission = registry.getRequiredPermission();

            if (new InstanceGroupManifest(hive).read() != null) {
                hid.type = HiveType.INSTANCE_GROUP;
            } else if (new SoftwareRepositoryManifest(hive).read() != null) {
                hid.type = HiveType.SOFTWARE_REPO;
            }

            result.add(hid);
        }

        Collections.sort(result, Comparator.comparing(d -> d.name));

        return result;
    }

    @Override
    public List<HiveEntryDto> listManifests(String hiveParam) {
        log.debug("listManifests(\"{}\")", hiveParam);
        BHive hive = registry.get(hiveParam);
        Set<Manifest.Key> allManifests = hive.execute(new ManifestListOperation());

        List<HiveEntryDto> result = new ArrayList<>();
        allManifests.stream().sorted(vss.getKeyComparator(null, null)).forEach(m -> {
            HiveEntryDto entry = new HiveEntryDto(m.toString(), Tree.EntryType.MANIFEST);
            entry.mName = m.getName();
            entry.mTag = m.getTag();
            result.add(entry);
        });
        return result;
    }

    @Override
    public List<HiveEntryDto> listManifest(String hiveParam, String name, String tag) {
        log.debug("listManifest(\"{}\",\"{}\",\"{}\")", hiveParam, name, tag);
        BHive hive = registry.get(hiveParam);
        Manifest.Key key = new Manifest.Key(name, tag);
        Manifest manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        return list(hive, manifest.getRoot());
    }

    @Override
    public List<HiveEntryDto> list(String hiveParam, String id) {
        log.debug("listManifest(\"{}\",\"{}\")", hiveParam, id);
        ObjectId treeId = ObjectId.parse(id);
        if (treeId == null) {
            throw new WebApplicationException("Invalid object ID " + id);
        }
        return list(registry.get(hiveParam), treeId);
    }

    @Override
    public Response download(String hiveParam, String id) {
        log.debug("download(\"{}\",\"{}\")", hiveParam, id);
        BHive hive = registry.get(hiveParam);
        StreamingOutput fileStream = new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(ObjectId.parse(id)))) {
                    is.transferTo(output);
                }
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM).build();
    }

    @Override
    public Response download(String hiveParam, HiveEntryDto dto) {
        log.debug("download(\"{}\",\"{}\",\"{}\",\"{}\")", hiveParam, dto.id, dto.mName, dto.mTag);
        BHive hive = registry.get(hiveParam);
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        ObjectId id = (dto.id != null && !dto.id.isBlank()) ? ObjectId.parse(dto.id)
                : hive.execute(new ManifestLoadOperation().setManifest(new Manifest.Key(dto.mName, dto.mTag))).getRoot();
        switch (dto.type) {
            case TREE:
                return ds.download(ds.downloadBHiveContent(hive, id, dto.name));
            case MANIFEST:
                return ds.download(ds.createOriginalZipAndRegister(hive, dto.mName, dto.mTag));
            default:
                return download(hiveParam, dto.id);
        }
    }

    private List<HiveEntryDto> list(BHive hive, ObjectId objectId) {
        Tree tree = hive.execute(new TreeLoadOperation().setTree(objectId));
        List<HiveEntryDto> result = new ArrayList<>();
        tree.getChildren().keySet().stream().forEach(key -> {
            HiveEntryDto entry = new HiveEntryDto(key.getName(), key.getType());
            ObjectId entryOid = tree.getChildren().get(key);
            entry.id = entryOid.getId();
            entry.size = hive.execute(new ObjectSizeOperation().addObject(entryOid));
            if (key.getType() == EntryType.MANIFEST) {
                SortedMap<ObjectId, Key> r = hive.execute(new ManifestRefLoadOperation().addManifestRef(entryOid));
                Key ref = r.get(entryOid);
                try {
                    // if this does not succeed, the reference is broken
                    hive.execute(new ManifestLoadOperation().setManifest(ref));
                    entry.mName = ref.getName();
                    entry.mTag = ref.getTag();
                } catch (Exception e) {
                    entry.name = entry.name + " (missing)";
                }
            }
            result.add(entry);
        });
        return result;
    }

    @Override
    public RepairAndPruneResultDto repairAndPrune(String hiveParam, boolean fix) {
        RepairAndPruneResultDto result = new RepairAndPruneResultDto();
        result.repaired = fsck(hiveParam, fix);
        result.pruned = prune(hiveParam);
        return result;
    }

    private String prune(String hiveParam) {
        try (var handle = af.run(Actions.PRUNE_BHIVE, hiveParam)) {
            log.debug("prune(\"{}\")", hiveParam);
            BHive hive = registry.get(hiveParam);

            SortedMap<ObjectId, Long> pruned = hive.execute(new PruneOperation());
            Long sumFreedBytes = pruned.entrySet().stream().collect(Collectors.summingLong(Map.Entry::getValue));

            return FormatHelper.formatFileSize(sumFreedBytes);
        }
    }

    private Map<String, String> fsck(String hiveParam, boolean fix) {
        try (var handle = af.run(Actions.FSCK_BHIVE, hiveParam)) {
            log.debug("fsck(\"{}\")", hiveParam);
            BHive hive = registry.get(hiveParam);

            Set<ElementView> problematic = hive.execute(new FsckOperation().setRepair(fix));
            Map<String, String> result = new TreeMap<>();
            problematic.forEach(e -> result.put(e.getElementId().getId(), e.getPathString()));
            return result;
        }
    }

    @Override
    public void delete(String hiveParam, String manifestName, String manifestTag) {
        log.debug("delete(\"{}\", \"{}\", \"{}\")", hiveParam, manifestName, manifestTag);
        BHive hive = registry.get(hiveParam);

        hive.execute(new ManifestDeleteOperation().setToDelete(new Manifest.Key(manifestName, manifestTag)));
    }

    @Override
    public void enablePool(String hive) {
        BHive bh = registry.get(hive);

        if (bh.isPooling() || minion.getDefaultPoolPath() == null) {
            return;
        }

        try (var handle = af.run(Actions.ENABLE_POOL, hive)) {
            bh.enablePooling(minion.getDefaultPoolPath(), false);
        }
    }

    @Override
    public void disablePool(String hive) {
        BHive bh = registry.get(hive);

        if (!bh.isPooling()) {
            return;
        }

        try (var handle = af.run(Actions.DISABLE_POOL, hive)) {
            bh.disablePooling();
        }
    }

    @Override
    public HiveLoggingResource getLoggingResource(String hive) {
        return rc.initResource(new HiveLoggingResourceImpl(hive));
    }
}
