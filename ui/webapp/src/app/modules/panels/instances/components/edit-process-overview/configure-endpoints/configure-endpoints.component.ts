import {
  AfterViewInit,
  Component,
  OnDestroy,
  QueryList,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable, of, Subscription } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { HttpAuthenticationType } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

@Component({
  selector: 'app-configure-endpoints',
  templateUrl: './configure-endpoints.component.html',
})
export class ConfigureEndpointsComponent
  implements DirtyableDialog, OnDestroy, AfterViewInit
{
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChildren('epForm') private forms: QueryList<NgForm>;

  /* template */ authTypeValues = Object.keys(HttpAuthenticationType);
  /* template */ authTypeLabels = Object.keys(HttpAuthenticationType).map(
    (t) => t.substring(0, 1) + t.substring(1).toLowerCase()
  );
  /* template */ hasPendingChanges: boolean;
  /* template */ isFromInvalid: boolean;

  private subscription: Subscription;

  constructor(
    public edit: ProcessEditService,
    public instanceEdit: InstanceEditService
  ) {}

  ngAfterViewInit(): void {
    if (!this.forms) {
      return;
    }
    this.forms.forEach((form) => {
      this.subscription = form.statusChanges
        .pipe(debounceTime(100))
        .subscribe((status) => {
          this.isFromInvalid = status === 'INVALID';
          this.hasPendingChanges = this.isDirty();
        });
    });
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  public isInvalid(): boolean {
    return this.forms.filter((f) => f.invalid).length !== 0;
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap(() => {
        this.instanceEdit.conceal('Change endpoint configuration');
      })
    );
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}
