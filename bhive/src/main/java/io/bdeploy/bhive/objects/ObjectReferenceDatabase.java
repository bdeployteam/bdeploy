package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.PathHelper;

/**
 * A special database which keeps track of references to a given {@link ObjectId}.
 * <p>
 * References can be expressed as simple {@link String}, and are typically meant to identify a {@link BHive} by its name.
 */
public class ObjectReferenceDatabase extends ObjectDatabase {

    public ObjectReferenceDatabase(Path root, ActivityReporter reporter) {
        super(root, root.resolve("tmp"), reporter, null);
    }

    public void addReference(ObjectId id, String hiveId) {
        locked(() -> {
            // potentially read existing entries.
            SortedSet<String> current = new TreeSet<>();
            if (hasObject(id)) {
                current.addAll(read(id));
            }

            current.add(hiveId);
            write(id, current);
        });
    }

    public SortedSet<String> read(ObjectId id) {
        if (!hasObject(id)) {
            return Collections.emptySortedSet();
        }

        try (InputStream is = Files.newInputStream(getObjectFile(id))) {
            return JacksonHelper.getDefaultJsonObjectMapper().readValue(is, new TypeReference<SortedSet<String>>() {
            });
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot read references from object counter db", ioe);
        }
    }

    private void write(ObjectId id, SortedSet<String> references) {
        Path refFile = getObjectFile(id);
        PathHelper.mkdirs(refFile.getParent());

        try (OutputStream os = Files.newOutputStream(refFile)) {
            JacksonHelper.getDefaultJsonObjectMapper().writeValue(os, references);
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot write references to object counter database", ioe);
        }
    }

    @Override
    public InputStream getStream(ObjectId id) throws IOException {
        throw new UnsupportedOperationException("Reference-only Database");
    }

    @Override
    protected ObjectId internalAddObject(ObjectWriter writer) throws IOException {
        throw new UnsupportedOperationException("Reference-only Database");
    }

    @Override
    public void removeObject(ObjectId id) {
        throw new UnsupportedOperationException("Reference-only Database");
    }

}
