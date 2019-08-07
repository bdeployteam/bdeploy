/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.objects.DbTestBase;
import io.bdeploy.bhive.util.StorageHelper;

public class SerializerTest extends DbTestBase {

    private final ObjectId OID1 = randomId();
    private final ObjectId OID2 = randomId();

    @Test
    public void serializeOid() {
        assertThat(OID1.compareTo(roundtrip(OID1)), is(0));
    }

    @Test
    public void serializeTree() {
        ObjectId id1 = OID1;
        ObjectId id2 = OID2;

        Tree.Key key1 = new Tree.Key("xx1", Tree.EntryType.BLOB);
        Tree.Key key2 = new Tree.Key("xx2", Tree.EntryType.TREE);

        Tree t = new Tree.Builder().add(key1, id1).add(key2, id2).build();
        assertThat(t.getChildren().size(), is(2));
        assertThat(t.getChildren().get(key1), is(notNullValue()));
        assertThat(t.getChildren().get(key2), is(notNullValue()));
        assertThat(t.getChildren().get(key1).compareTo(id1), is(0));
        assertThat(t.getChildren().get(key2).compareTo(id2), is(0));

        Tree r = roundtrip(t);
        assertThat(r.getChildren().size(), is(2));
        assertThat(r.getChildren().get(key1), is(notNullValue()));
        assertThat(r.getChildren().get(key2), is(notNullValue()));
        assertThat(r.getChildren().get(key1).compareTo(id1), is(0));
        assertThat(r.getChildren().get(key2).compareTo(id2), is(0));

        for (Tree.Key k : r.getChildren().keySet()) {
            switch (k.getName()) {
                case "xx1":
                    assertThat(k.getType(), is(Tree.EntryType.BLOB));
                    break;
                case "xx2":
                    assertThat(k.getType(), is(Tree.EntryType.TREE));
                    break;
            }
        }
    }

    @Test
    public void testEntryOrder() {
        Tree.Key k1 = new Tree.Key("a", Tree.EntryType.BLOB);
        Tree.Key k2 = new Tree.Key("z", Tree.EntryType.BLOB);

        Tree.Builder builder = new Tree.Builder();

        builder.add(k1, OID1);
        builder.add(k2, OID2);

        byte[] bytes1 = StorageHelper.toRawBytes(builder.build());

        Tree.Builder reverse = new Tree.Builder();

        reverse.add(k2, OID2);
        reverse.add(k1, OID1);

        byte[] bytes2 = StorageHelper.toRawBytes(reverse.build());

        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    public void serializeManifest() {
        ObjectId id1 = OID1;

        String timeStr = Long.toString(System.currentTimeMillis());
        Manifest m = new Manifest.Builder(new Manifest.Key("test-app", "v1")).setRoot(id1).addLabel("build-time", timeStr)
                .build(null);
        Manifest r = roundtrip(m);

        assertThat(r.getRoot().compareTo(id1), is(0));
        assertThat(r.getKey().getName(), is("test-app"));
        assertThat(r.getLabels().get("build-time"), is(timeStr));
    }

    @SuppressWarnings("unchecked")
    private <T> T roundtrip(T obj) {
        try {
            byte[] bytes = StorageHelper.toRawBytes(obj);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                return (T) StorageHelper.fromStream(bis, obj.getClass());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
