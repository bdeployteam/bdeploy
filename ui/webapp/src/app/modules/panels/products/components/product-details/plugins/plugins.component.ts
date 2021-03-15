import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { ProductDetailsService } from '../../../services/product-details.service';

const pluginNameColumn: BdDataColumn<PluginInfoDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '130px',
};

const pluginVersionColumn: BdDataColumn<PluginInfoDto> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.version,
  width: '100px',
};

const pluginOIDColumn: BdDataColumn<PluginInfoDto> = {
  id: 'oid',
  name: 'OID',
  data: (r) => r.id.id,
  width: '50px',
};

@Component({
  selector: 'app-plugins',
  templateUrl: './plugins.component.html',
  styleUrls: ['./plugins.component.css'],
  providers: [ProductDetailsService],
})
export class PluginsComponent implements OnInit {
  /* template */ columns: BdDataColumn<PluginInfoDto>[] = [pluginNameColumn, pluginVersionColumn, pluginOIDColumn];

  constructor(public singleProduct: ProductDetailsService) {}

  ngOnInit(): void {}
}
