import { Component, forwardRef, inject, Input } from '@angular/core';
import { HiveEntryDto } from 'src/app/models/gen.dtos';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';
import { BHiveBrowserComponent } from '../bhive-browser.component';

@Component({
  selector: 'app-manifest-delete-action',
  templateUrl: './manifest-delete-action.component.html',
})
export class ManifestDeleteActionComponent {
  private hives = inject(HiveService);
  private parent = inject(forwardRef(() => BHiveBrowserComponent));

  @Input() record: HiveEntryDto;

  protected onDelete(): void {
    this.parent.dialog
      .confirm(`Delete ${this.record.name}?`, `This will remove the manifest permanently from the enclosing BHive.`)
      .subscribe((r) => {
        if (r) {
          this.hives
            .delete(this.parent.bhive$.value, this.record.mName, this.record.mTag)
            .subscribe(() => this.parent.load());
        }
      });
  }
}
