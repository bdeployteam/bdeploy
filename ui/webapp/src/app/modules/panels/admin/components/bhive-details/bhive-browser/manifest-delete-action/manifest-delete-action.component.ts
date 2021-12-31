import { Component, forwardRef, Inject, Input, OnInit } from '@angular/core';
import { HiveEntryDto } from 'src/app/models/gen.dtos';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';
import { BHiveBrowserComponent } from '../bhive-browser.component';

@Component({
  selector: 'app-manifest-delete-action',
  templateUrl: './manifest-delete-action.component.html',
  styleUrls: ['./manifest-delete-action.component.css'],
})
export class ManifestDeleteActionComponent implements OnInit {
  @Input() record: HiveEntryDto;

  constructor(private hives: HiveService, @Inject(forwardRef(() => BHiveBrowserComponent)) private parent: BHiveBrowserComponent) {}

  ngOnInit(): void {}

  /* template */ onDelete(): void {
    this.parent.dialog.confirm(`Delete ${this.record.name}?`, `This will remove the manifest permanently from the enclosing BHive.`).subscribe((r) => {
      if (r) {
        this.hives.delete(this.parent.bhive$.value, this.record.mName, this.record.mTag).subscribe((_) => this.parent.load());
      }
    });
  }
}
