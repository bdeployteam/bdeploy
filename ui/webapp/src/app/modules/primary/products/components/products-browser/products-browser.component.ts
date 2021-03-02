import { Component, OnDestroy, OnInit } from '@angular/core';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { ProductsColumnsService } from '../../services/products-columns.service';
import { ProductsService } from '../../services/products.service';

@Component({
  selector: 'app-products-browser',
  templateUrl: './products-browser.component.html',
  styleUrls: ['./products-browser.component.css'],
})
export class ProductsBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<ProductDto>[] = [{ name: 'Product ID', group: (r) => r.product }];
  defaultGrouping: BdDataGrouping<ProductDto>[] = [{ definition: this.grouping[0], selected: [] }];

  /* template */ getRecordRoute = (row: ProductDto) => {
    return ['', { outlets: { panel: ['panels', 'products', 'details', row.key.name, row.key.tag] } }];
  };

  constructor(
    public products: ProductsService,
    public productColumns: ProductsColumnsService,
    public groups: GroupsService,
    public auth: AuthenticationService
  ) {}

  ngOnInit(): void {}

  ngOnDestroy(): void {}
}
