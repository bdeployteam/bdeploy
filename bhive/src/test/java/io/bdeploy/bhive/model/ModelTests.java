/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.objects.DbTestBase;

class ModelTests extends DbTestBase {

    @Test
    void testDuplicatePath() {
        Tree.Key k1 = new Tree.Key("XX", Tree.EntryType.BLOB);
        Tree.Key k2 = new Tree.Key("XX", Tree.EntryType.TREE);

        ObjectId id1 = ObjectId.parse("1a3eb5e2881794d954afa69a3f407cc20cd6a2cc");
        ObjectId id2 = ObjectId.parse("1c00d6575b566e924261356e8ac58f347fbdebeb");

        Tree.Builder builder = new Tree.Builder();
        builder.add(k1, id1);
        assertThrows(RuntimeException.class, () -> {
            builder.add(k2, id2);
        });
    }

}
