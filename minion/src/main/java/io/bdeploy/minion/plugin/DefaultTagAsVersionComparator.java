package io.bdeploy.minion.plugin;

import java.io.Serializable;
import java.util.Comparator;

class DefaultTagAsVersionComparator implements Comparator<String>, Serializable {

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
            boolean aIsnum = aValue.matches(digit);
            boolean bIsnum = bValue.matches(digit);

            int comp;
            if (aIsnum && bIsnum) {
                try {
                    Long aNum = Long.valueOf(aValue);
                    Long bNum = Long.valueOf(bValue);
                    comp = aNum.compareTo(bNum);
                } catch (NumberFormatException e) {
                    comp = aValue.compareTo(bValue);
                }
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
