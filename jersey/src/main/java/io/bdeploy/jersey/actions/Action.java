package io.bdeploy.jersey.actions;

import java.util.Comparator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.RuntimeAssert;
import jakarta.annotation.Generated;

public class Action implements Comparable<Action> {

    private static final Comparator<Action> COMPARATOR = Comparator.comparing(Action::getType)
            .thenComparing(Action::getBHive, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Action::getInstance, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Action::getItem, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final Actions type;
    private final String bhive;
    private final String instance;
    private final String item;

    @JsonCreator
    public Action(@JsonProperty("type") Actions type, @JsonProperty("bhive") String bhive,
            @JsonProperty("instance") String instance, @JsonProperty("item") String item) {
        this.type = type;
        this.bhive = bhive;
        this.instance = instance;
        this.item = item;

        switch (type.getScope()) {
            case GLOBAL:
                RuntimeAssert.assertNull(bhive, "BHive may not be set");
                RuntimeAssert.assertNull(instance, "Instance may not be set");
                break;
            case BHIVE:
                RuntimeAssert.assertNotNull(bhive, "BHive must be set");
                RuntimeAssert.assertNull(instance, "Instance may not be set");
                break;
            case INSTANCE:
                RuntimeAssert.assertNotNull(bhive, "BHive must be set");
                RuntimeAssert.assertNotNull(instance, "Instance must be set");
                break;
            case VERSION:
            case PROCESS:
                RuntimeAssert.assertNotNull(bhive, "BHive must be set");
                RuntimeAssert.assertNotNull(instance, "Instance must be set");
                RuntimeAssert.assertNotNull(item, "Item must be set");
                break;
        }
    }

    public Actions getType() {
        return type;
    }

    public String getBHive() {
        return bhive;
    }

    public String getInstance() {
        return instance;
    }

    public String getItem() {
        return item;
    }

    @Override
    public int compareTo(Action o) {
        return COMPARATOR.compare(this, o);
    }

    @Override
    public String toString() {
        String baseName = "Action " + type.name();
        switch (type.getScope()) {
            case GLOBAL:
                return "Global " + baseName;
            case BHIVE:
                return baseName + " on " + bhive;
            case INSTANCE:
                return baseName + " on " + bhive + "/" + instance;
            case VERSION:
            case PROCESS:
                return baseName + " on " + bhive + "/" + instance + " (" + item + ")";
            default:
                return baseName + " in unknown scope";
        }

    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bhive == null) ? 0 : bhive.hashCode());
        result = prime * result + ((instance == null) ? 0 : instance.hashCode());
        result = prime * result + ((item == null) ? 0 : item.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Action other = (Action) obj;
        if (bhive == null) {
            if (other.bhive != null) {
                return false;
            }
        } else if (!bhive.equals(other.bhive)) {
            return false;
        }
        if (instance == null) {
            if (other.instance != null) {
                return false;
            }
        } else if (!instance.equals(other.instance)) {
            return false;
        }
        if (item == null) {
            if (other.item != null) {
                return false;
            }
        } else if (!item.equals(other.item)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

}
