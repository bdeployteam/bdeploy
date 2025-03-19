import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import {
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  PluginInfoDto,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { SwRepositoryEntry } from '../../primary/repositories/services/repository.service';

export interface LabelRecord {
  key: string;
  value: string;
}

export interface InstTemplateData {
  productName: string;
  productId: string;
  config: FlattenedInstanceTemplateConfiguration;
}

export interface InstTemplateColumnData extends InstTemplateData {
  downloadHook: (data: InstTemplateData) => void;
}

@Injectable({
  providedIn: 'root',
})
export class ProductActionsColumnsService {
  private readonly labelKeyColumn: BdDataColumn<LabelRecord, string> = {
    id: 'key',
    name: 'Label',
    data: (r) => r.key,
    isId: true,
    width: '90px',
  };

  private readonly labelValueColumn: BdDataColumn<LabelRecord, string> = {
    id: 'value',
    name: 'Value',
    data: (r) => r.value,
    width: '190px',
  };

  private readonly appTemplateNameColumn: BdDataColumn<FlattenedApplicationTemplateConfiguration, string> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true,
    tooltip: (r) => r.description,
  };

  private readonly instTemplateNameColumn: BdDataColumn<InstTemplateColumnData, string> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.config.name,
    isId: true,
    tooltip: (r) => r.config.description,
  };

  private readonly instTemplateResponseFileDownloadColumn: BdDataColumn<InstTemplateColumnData, string> = {
    id: 'downloadResponseFile',
    name: 'Response File',
    data: () => 'Download a response file for this instance template',
    action: (r) => r.downloadHook(r),
    icon: () => 'download',
    width: '0px',
    classes: () => ['justify-items-center'],
  };

  private readonly pluginNameColumn: BdDataColumn<PluginInfoDto, string> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    width: '130px',
  };

  private readonly pluginVersionColumn: BdDataColumn<PluginInfoDto, string> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.version,
    width: '100px',
  };

  private readonly pluginOIDColumn: BdDataColumn<PluginInfoDto, string> = {
    id: 'oid',
    name: 'OID',
    data: (r) => r.id.id,
    isId: true,
    width: '50px',
  };

  public readonly defaultLabelsColumns: BdDataColumn<LabelRecord, unknown>[] = //
    [this.labelKeyColumn, this.labelValueColumn];

  public readonly defaultApplicationTemplatesColumns: BdDataColumn<FlattenedApplicationTemplateConfiguration, unknown>[] = //
    [this.appTemplateNameColumn];

  public readonly defaultInstanceTemplatesColumns: BdDataColumn<InstTemplateColumnData, unknown>[] = //
    [this.instTemplateNameColumn, this.instTemplateResponseFileDownloadColumn];

  public readonly defaultPluginsColumns: BdDataColumn<PluginInfoDto, unknown>[] = //
    [this.pluginNameColumn, this.pluginVersionColumn, this.pluginOIDColumn];

  public mapToAppTemplateColumnData(
    product: ProductDto | SwRepositoryEntry,
    downloadHook: (data: InstTemplateData) => void,
  ): InstTemplateColumnData[] {
    return product.instanceTemplates.map((config) => ({
      productName: product.name,
      productId: product.product,
      config,
      downloadHook,
    }));
  }
}
