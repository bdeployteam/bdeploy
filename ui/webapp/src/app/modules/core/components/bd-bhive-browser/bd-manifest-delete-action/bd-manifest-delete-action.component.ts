import { Component, forwardRef, inject, Input } from '@angular/core';
import { HiveEntryDto } from 'src/app/models/gen.dtos';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';
import { AuthenticationService } from '../../../services/authentication.service';
import { BdBHiveBrowserComponent } from '../bd-bhive-browser.component';
import { BdButtonComponent } from '../../bd-button/bd-button.component';
import { ClickStopPropagationDirective } from '../../../directives/click-stop-propagation.directive';
import { AsyncPipe } from '@angular/common';
import { BdDataColumn } from '../../../../../models/data';
import { TableCellDisplay } from '../../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-manifest-delete-action',
    templateUrl: './bd-manifest-delete-action.component.html',
    imports: [BdButtonComponent, ClickStopPropagationDirective, AsyncPipe]
})
export class BdManifestDeleteActionComponent implements TableCellDisplay<HiveEntryDto> {
  private readonly hives = inject(HiveService);
  private readonly parent: BdBHiveBrowserComponent = inject(forwardRef(() => BdBHiveBrowserComponent));
  protected readonly auth = inject(AuthenticationService);

  @Input() record: HiveEntryDto;
  @Input() column: BdDataColumn<HiveEntryDto>;

  protected onDelete(): void {
    this.parent.dialog
      .confirm(`Delete ${this.record.name}?`, `This will remove the manifest permanently from the enclosing BHive.`)
      .subscribe((r: boolean) => {
        if (r) {
          this.hives
            .delete(this.parent.bhive$.value, this.record.mName, this.record.mTag)
            .subscribe(() => this.parent.load());
        }
      });
  }
}
