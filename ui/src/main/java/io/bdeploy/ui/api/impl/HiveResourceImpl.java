package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

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
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.ui.api.HiveResource;
import io.bdeploy.ui.dto.HiveEntryDto;

public class HiveResourceImpl implements HiveResource {

    private static final Logger log = LoggerFactory.getLogger(HiveResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Override
    public List<String> listHives() {
        log.debug("listHives()");
        ArrayList<String> list = new ArrayList<>(registry.getAll().keySet());
        Collections.sort(list);
        return list;
    }

    @Override
    public List<HiveEntryDto> listManifests(String hiveParam) {
        log.debug("listManifests(\"{}\")", hiveParam);
        BHive hive = registry.get(hiveParam);
        SortedSet<Manifest.Key> allManifests = hive.execute(new ManifestListOperation());

        List<HiveEntryDto> result = new ArrayList<>();
        allManifests.forEach(m -> {
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
        BHive hive = registry.get(hiveParam);
        ObjectId treeId = ObjectId.parse(id);
        if (treeId == null) {
            throw new WebApplicationException("Invalid object ID " + id);
        }
        return list(hive, treeId);
    }

    @Override
    public Response downloadManifest(String hiveParam, String name, String tag) {
        log.debug("downloadManifest(\"{}\",\"{}\",\"{}\")", hiveParam, name, tag);
        BHive hive = registry.get(hiveParam);
        Manifest.Key key = new Manifest.Key(name, tag);
        Manifest manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        StreamingOutput fileStream = new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                output.write(StorageHelper.toRawBytes(manifest));
                output.flush();
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM).build();
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

    private List<HiveEntryDto> list(BHive hive, ObjectId objectId) {
        Tree tree = hive.execute(new TreeLoadOperation().setTree(objectId));
        List<HiveEntryDto> result = new ArrayList<>();
        for (Tree.Key key : tree.getChildren().keySet()) {
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
        }
        return result;
    }

    @Override
    public String prune(String hiveParam) {
        log.debug("prune(\"{}\")", hiveParam);
        BHive hive = registry.get(hiveParam);

        SortedMap<ObjectId, Long> pruned = hive.execute(new PruneOperation());
        Long sumFreedBytes = pruned.entrySet().stream().collect(Collectors.summingLong(Map.Entry::getValue));

        return UnitHelper.formatFileSize(sumFreedBytes);
    }

    @Override
    public Map<String, String> fsck(String hiveParam, boolean fix) {
        log.debug("fsck(\"{}\")", hiveParam);
        BHive hive = registry.get(hiveParam);

        try (Activity fsck = reporter.start("FSCK " + hiveParam)) {
            List<ElementView> problematic = hive.execute(new FsckOperation().setRepair(fix));
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

}
