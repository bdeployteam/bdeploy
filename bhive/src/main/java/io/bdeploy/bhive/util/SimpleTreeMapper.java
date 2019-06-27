package io.bdeploy.bhive.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.base.Splitter;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.util.StorageHelper.CustomMapper;

/**
 * Custom tree serialization/de-serialization. JSON/YAML is simply too slow for the amount of objects.
 */
public class SimpleTreeMapper implements CustomMapper {

    @Override
    public byte[] write(Object obj) {
        Tree t = (Tree) obj;
        StringBuilder builder = new StringBuilder();

        for (Entry<Key, ObjectId> entry : t.getChildren().entrySet()) {
            builder.append(entry.getKey().getType().name()).append('|');
            builder.append(entry.getValue().toString()).append('|');
            builder.append("").append('|'); // field reserved for future use
            builder.append(entry.getKey().getName()).append('\n');
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object read(InputStream is) {
        Tree.Builder builder = new Tree.Builder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                List<String> parts = Splitter.on('|').splitToList(line);
                builder.add(new Tree.Key(parts.get(3), EntryType.valueOf(parts.get(0))), ObjectId.parse(parts.get(1)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read tree", e);
        }
        return builder.build();
    }

}
