import { AfterViewInit, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { debounceTime, finalize, map } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../../services/server-details.service';


import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../../core/validators/trimmed.directive';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';

@Component({
    selector: 'app-server-edit',
    templateUrl: './server-edit.component.html',
    providers: [ServerDetailsService],
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, TrimmedValidator, BdButtonComponent]
})
export class ServerEditComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  private readonly servers = inject(ServersService);
  private readonly areas = inject(NavAreasService);
  protected readonly details = inject(ServerDetailsService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected loading$ = combineLatest([this.saving$, this.servers.loading$, this.details.loading$]).pipe(
    map(([a, b, c]) => a || b || c),
  );

  protected server: ManagedMasterDto;
  protected orig: ManagedMasterDto;
  protected disableSave: boolean;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;
  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      this.details.server$.subscribe((s) => {
        this.server = cloneDeep(s);
        this.orig = cloneDeep(s);
      }),
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.disableSave = this.isDirty();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.server, this.orig);
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.orig = cloneDeep(this.server);
      this.tb.closePanel();
    });
  }

  public doSave() {
    this.saving$.next(true);
    return this.details.update(this.server).pipe(finalize(() => this.saving$.next(false)));
  }
}
