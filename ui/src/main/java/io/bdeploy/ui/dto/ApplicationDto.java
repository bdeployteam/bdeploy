package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

public class ApplicationDto {

    public String name;
    public Manifest.Key key;

    public ApplicationDescriptor descriptor;

}