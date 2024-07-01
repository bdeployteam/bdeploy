import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'add-global-attribute',
  templateUrl: './add-global-attribute.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddGlobalAttributeComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected tempAttribute: CustomAttributeDescriptor;
  protected tempUsedIds: string[];
  protected saving$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.tempAttribute = { name: '', description: '' };
    this.tempUsedIds = this.settings.settings$.value.instanceGroup.attributes.map((a) => a.name);
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        }),
      )
      .subscribe(() => {
        this.areas.closePanel();
        this.subscription?.unsubscribe();
      });
  }

  public doSave(): Observable<boolean> {
    this.saving$.next(true);
    return this.settings.addGlobalAttribute(this.tempAttribute);
  }
}
