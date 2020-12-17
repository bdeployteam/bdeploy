package io.bdeploy.bhive.util;

import java.util.Comparator;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.VersionHelper;

public class VersionComparator {

    public static final Comparator<Key> BY_TAG_NEWEST_LAST = (a, b) -> VersionHelper.compare(a.getTag(), b.getTag());

    public static final Comparator<Version> NEWEST_LAST = VersionHelper::compare;

    private VersionComparator() {
    }

}
