import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import {
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  PluginInfoDto,
} from 'src/app/models/gen.dtos';
import { LabelRecord } from '../../panels/products/services/product-details.service';

@Injectable({
  providedIn: 'root',
})
export class ProductActionsColumnsService {
  private readonly labelKeyColumn: BdDataColumn<LabelRecord> = {
    id: 'key',
    name: 'Label',
    data: (r) => r.key,
    isId: true,
    width: '90px',
  };

  private readonly labelValueColumn: BdDataColumn<LabelRecord> = {
    id: 'value',
    name: 'Value',
    data: (r) => r.value,
    width: '190px',
  };

  private readonly appTemplateNameColumn: BdDataColumn<FlattenedApplicationTemplateConfiguration> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true,
    tooltip: (r) => r.description,
  };

  private readonly instTemplateNameColumn: BdDataColumn<FlattenedInstanceTemplateConfiguration> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true,
    tooltip: (r) => r.description,
  };

  private readonly pluginNameColumn: BdDataColumn<PluginInfoDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    width: '130px',
  };

  private readonly pluginVersionColumn: BdDataColumn<PluginInfoDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.version,
    width: '100px',
  };

  private readonly pluginOIDColumn: BdDataColumn<PluginInfoDto> = {
    id: 'oid',
    name: 'OID',
    data: (r) => r.id.id,
    isId: true,
    width: '50px',
  };

  public readonly defaultLabelsColumns: BdDataColumn<LabelRecord>[] = //
    [this.labelKeyColumn, this.labelValueColumn];

  public readonly defaultApplicationTemplatesColumns: BdDataColumn<FlattenedApplicationTemplateConfiguration>[] = //
    [this.appTemplateNameColumn];

  public readonly defaultInstanceTemplatesColumns: BdDataColumn<FlattenedInstanceTemplateConfiguration>[] = //
    [this.instTemplateNameColumn];

  public readonly defaultPluginsColumns: BdDataColumn<PluginInfoDto>[] = //
    [this.pluginNameColumn, this.pluginVersionColumn, this.pluginOIDColumn];
}
