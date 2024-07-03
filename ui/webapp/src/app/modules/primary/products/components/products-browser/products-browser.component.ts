import { Component, OnInit, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
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
  private readonly cardViewService = inject(CardViewService);
  protected readonly cfg = inject(ConfigService);
  protected readonly products = inject(ProductsService);
  protected readonly productColumns = inject(ProductsColumnsService);
  protected readonly groups = inject(GroupsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly bulk = inject(ProductBulkService);

  protected grouping: BdDataGroupingDefinition<ProductDto>[] = [{ name: 'Product ID', group: (r) => r.product }];
  protected defaultGrouping: BdDataGrouping<ProductDto>[] = [{ definition: this.grouping[0], selected: [] }];

  protected getRecordRoute = (row: ProductDto) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'products', 'details', row.key.name, row.key.tag],
        },
      },
    ];
  };

  protected isCardView: boolean;
  protected presetKeyValue = 'products';
  protected sort: Sort = { active: 'version', direction: 'desc' };

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
