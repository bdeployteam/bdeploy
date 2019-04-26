/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.DbTestBase;

public class ModelTests extends DbTestBase {

    @Test
    public void testDuplicatePath() {
        Tree.Key k1 = new Tree.Key("XX", Tree.EntryType.BLOB);
        Tree.Key k2 = new Tree.Key("XX", Tree.EntryType.TREE);

        ObjectId id1 = randomId();
        ObjectId id2 = randomId();

        Tree.Builder builder = new Tree.Builder();
        builder.add(k1, id1);
        assertThrows(RuntimeException.class, () -> {
            builder.add(k2, id2);
        });
    }

}
