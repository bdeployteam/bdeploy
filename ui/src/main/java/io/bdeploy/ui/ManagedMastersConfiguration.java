package io.bdeploy.ui;

import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.ui.dto.ManagedMasterDto;

/**
 * A DTO keeping track of all the attached managed masters. This is used on the central master only
 */
public class ManagedMastersConfiguration {

    private final SortedMap<String, ManagedMasterDto> attachedManagedMasters = new TreeMap<>();

    /**
     * @return all currently known attached managed servers by name.
     */
    public SortedMap<String, ManagedMasterDto> getManagedMasters() {
        return attachedManagedMasters;
    }

    /**
     * attach a new, or replace an existing attached managed master by name.
     */
    public void addManagedMaster(ManagedMasterDto dto) {
        attachedManagedMasters.put(dto.name, dto);
    }

    /**
     * remove an existing attahced managed master
     */
    public void removeManagedMaster(String name) {
        attachedManagedMasters.remove(name);
    }

    /**
     * @return the named attached managed master.
     */
    public ManagedMasterDto getManagedMaster(String name) {
        return attachedManagedMasters.get(name);
    }

}
