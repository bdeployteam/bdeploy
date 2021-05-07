import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPanelButtonComponent } from 'src/app/modules/core/components/bd-panel-button/bd-panel-button.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

@Component({
  selector: 'app-configure-process',
  templateUrl: './configure-process.component.html',
  styleUrls: ['./configure-process.component.css'],
})
export class ConfigureProcessComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);

  @ViewChild('backButton') private back: BdPanelButtonComponent;
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(public edit: ProcessEditService, public instanceEdit: InstanceEditService, bop: BreakpointObserver) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }
  /* template */ doApply() {
    this.instanceEdit.conceal(`Edit ${this.edit.process$.value.name}`);
    this.back.onClick();
  }
}
