package io.bdeploy.common.util;

import java.io.Serializable;
import java.util.Comparator;

public class TagComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(String tagA, String tagB) {

        // split tags into parts, e.g. "5.9.0-N20190830" -> ["5", "9", "0", "N20190830"]
        String tagTokenRegex = "[^a-zA-Z0-9']+";
        String[] a = tagA.split(tagTokenRegex);
        String[] b = tagB.split(tagTokenRegex);

        // sort  by tokens
        for (int i = 0; i < Math.min(a.length, b.length); i++) {

            String aValue = a[i];
            String bValue = b[i];

            String digit = "\\d+";
            boolean a_isnum = aValue.matches(digit);
            boolean b_isnum = bValue.matches(digit);

            int comp;
            if (a_isnum && b_isnum) {
                Integer aNum = Integer.valueOf(aValue);
                Integer bNum = Integer.valueOf(bValue);
                comp = aNum.compareTo(bNum);
            } else {
                comp = aValue.compareTo(bValue);
            }

            if (comp == 0) {
                continue;
            }

            return comp;

        }
        return a.length - b.length;
    }
}
