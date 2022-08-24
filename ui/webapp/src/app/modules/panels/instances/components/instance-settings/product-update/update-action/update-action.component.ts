import { Component, Input, OnInit } from '@angular/core';
import { of } from 'rxjs';
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
    let askMigration = of('KEEP');
    if (this.needMigration) {
      askMigration = this.showMigrationWarning();
    }

    askMigration.subscribe((r) => {
      if (r === 'MIGRATE') {
        this.edit.migrateGlobals(this.edit.state$.value.config);
        this.edit.conceal(`Migrate global parameters to instance variables.`);
      }
      this.edit.updateProduct(this.record);
    });
  }

  /* template */ showMigrationWarning() {
    // FIXME: link to documenation chapter describing migration impact, scenarios, etc.

    // this is a "mild" hack to get hold of the primary dialog to show a message.
    return this.areas.getDirtyable('primary').dialog.message({
      header: 'Global Parameter Migration',
      message: `The concept of <strong>global parameters</strong> has been replaced by <strong>instance variables</strong>.<br/><br/>
                  This requires a migration step for each instance to use <strong>instance variables</strong>.<br/><br/>
                  For more details, see <a target="_blank" href="https://bdeploy.io/user/index.html#_migration_from_global_parameters_to_instance_variables">the online documentation.</a>`,
      actions: [
        { name: `Migrate`, result: 'MIGRATE', confirm: false },
        { name: `Keep for now`, result: 'KEEP', confirm: true },
      ],
    });
  }
}
