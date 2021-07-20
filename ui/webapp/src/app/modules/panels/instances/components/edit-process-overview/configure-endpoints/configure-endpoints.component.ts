import { Component, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import { HttpAuthenticationType } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

@Component({
  selector: 'app-configure-endpoints',
  templateUrl: './configure-endpoints.component.html',
  styleUrls: ['./configure-endpoints.component.css'],
})
export class ConfigureEndpointsComponent implements OnInit, DirtyableDialog {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChildren('epForm') private forms: QueryList<NgForm>;

  constructor(public edit: ProcessEditService, private instanceEdit: InstanceEditService) {}

  ngOnInit(): void {}

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  isInvalid(): boolean {
    return this.forms.filter((f) => f.invalid).length !== 0;
  }

  getAuthTypeValues() {
    return Object.keys(HttpAuthenticationType);
  }

  getAuthTypeLabels() {
    return Object.keys(HttpAuthenticationType).map((t) => t.substring(0, 1) + t.substring(1).toLowerCase());
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap((_) => {
        this.instanceEdit.conceal('Change endpoint configuration');
      })
    );
  }
}
