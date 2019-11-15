package io.bdeploy.ui.dto;

import java.util.List;

import io.bdeploy.ui.api.MinionMode;

public class ProductTransferDto {

    public MinionMode sourceMode;
    public String sourceServer;

    public MinionMode targetMode;
    public String targetServer;

    public List<ProductDto> versionsToTransfer;

}
