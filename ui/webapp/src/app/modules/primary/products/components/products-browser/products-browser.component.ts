import { Component, OnInit } from '@angular/core';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ProductBulkService } from 'src/app/modules/panels/products/services/product-bulk.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { ProductsColumnsService } from '../../services/products-columns.service';
import { ProductsService } from '../../services/products.service';

@Component({
  selector: 'app-products-browser',
  templateUrl: './products-browser.component.html',
})
export class ProductsBrowserComponent implements OnInit {
  grouping: BdDataGroupingDefinition<ProductDto>[] = [
    { name: 'Product ID', group: (r) => r.product },
  ];
  defaultGrouping: BdDataGrouping<ProductDto>[] = [
    { definition: this.grouping[0], selected: [] },
  ];

  /* template */ getRecordRoute = (row: ProductDto) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'products', 'details', row.key.name, row.key.tag],
        },
      },
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'products';

  constructor(
    public cfg: ConfigService,
    public products: ProductsService,
    public productColumns: ProductsColumnsService,
    public groups: GroupsService,
    public auth: AuthenticationService,
    public bulk: ProductBulkService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
