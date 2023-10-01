package io.bdeploy.interfaces.manifest.managed;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.Manifest;

/**
 * Stores keys of products installed on a managed server
 */
public class MinionProductsDto {

    public List<Manifest.Key> products = new ArrayList<>();

}
