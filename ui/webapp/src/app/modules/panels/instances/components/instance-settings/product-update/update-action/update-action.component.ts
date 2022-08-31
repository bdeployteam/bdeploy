import { Component, Input, OnInit } from '@angular/core';
import { map, Observable, of } from 'rxjs';
import { ProductDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-update-action',
  templateUrl: './update-action.component.html',
})
export class UpdateActionComponent implements OnInit {
  @Input() record: ProductDto;

  private index: number;
  private curIndex: number;
  /* template */ isUpgrade: boolean;
  /* template */ isCurrent: boolean;
  /* template */ needMigration: boolean;

  constructor(
    private products: ProductsService,
    private edit: InstanceEditService,
    private areas: NavAreasService
  ) {}

  ngOnInit(): void {
    const products = this.products.products$.value || [];
    this.index = products.indexOf(this.record);
    this.curIndex = products.findIndex(
      (r) =>
        this.edit.state$.value?.config.config.product.name === r.key.name &&
        this.edit.state$.value?.config.config.product.tag === r.key.tag
    );
    this.isUpgrade = this.index < this.curIndex;
    this.isCurrent = this.index === this.curIndex;

    this.needMigration =
      this.edit.serverSupportsVariables$.value &&
      !this.edit.globalsMigrated$.value;
  }

  /* template */ doUpdate() {
    let check = of(false);
    if (this.needMigration) {
      check = this.checkMigration();
    }

    check.subscribe((r) => {
      if (r) {
        this.edit.migrateGlobals(this.edit.state$.value.config);
        this.edit.conceal(`Migrate global parameters to instance variables.`);
      }
      this.edit.updateProduct(this.record);
    });
  }

  private checkMigration(): Observable<boolean> {
    // actually check the new product version if it still has globals. if it "no longer" has globals,
    // we migrate existing globals. If we did not have globals to start with, migration is a no-op.
    return this.products.loadApplications(this.record).pipe(
      map((a) => {
        // check without instance state and new product's applications. if migration is NOT
        // required, this means that the applications no longer have (or never had) globals.
        return !this.edit.isMigrationRequired(null, a);
      })
    );
  }
}
