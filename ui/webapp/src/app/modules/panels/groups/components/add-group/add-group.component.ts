import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Component({
  selector: 'app-add-group',
  templateUrl: './add-group.component.html',
  styleUrls: ['./add-group.component.css'],
})
export class AddGroupComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ group: Partial<InstanceGroupConfiguration> = { autoDelete: true };

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  private image: File;

  constructor(private groups: GroupsService, private areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {}

  /* template */ onSelectImage(image: File) {
    this.image = image;
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning').subscribe();
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        })
      )
      .subscribe((_) => {
        if (!!this.image) {
          this.groups.updateImage(this.group.name, this.image).subscribe((__) => {
            this.reset();
          });
        } else {
          this.reset();
        }
      });
  }

  private reset() {
    this.areas.closePanel();
    this.subscription.unsubscribe();
  }

  public doSave(): Observable<void> {
    this.saving$.next(true);
    return this.groups.create(this.group);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
