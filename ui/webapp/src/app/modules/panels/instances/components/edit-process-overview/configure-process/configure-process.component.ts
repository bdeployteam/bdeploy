import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Observable, Subscription, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

@Component({
  selector: 'app-configure-process',
  templateUrl: './configure-process.component.html',
})
export class ConfigureProcessComponent implements OnInit, OnDestroy, DirtyableDialog {
  private instanceEdit = inject(InstanceEditService);
  private areas = inject(NavAreasService);
  protected edit = inject(ProcessEditService);

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  protected hasPendingChanges: boolean;
  protected isInvalid: boolean;
  private isHeaderInvalid: boolean;
  private isParamGroupInvalid: boolean;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  public canSave(): boolean {
    return !this.isInvalid;
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap(() => {
        this.edit.alignGlobalParameters(this.edit.application$.value, this.edit.process$.value);
        this.instanceEdit.conceal(`Edit ${this.edit.process$.value.name}`);
      })
    );
  }

  protected checkIsInvalid(event: boolean, isHeader: boolean) {
    if (isHeader) {
      this.isHeaderInvalid = event;
    } else {
      this.isParamGroupInvalid = event;
    }
    this.isInvalid = this.isHeaderInvalid || this.isParamGroupInvalid;
    this.hasPendingChanges = this.isDirty();
  }
}
