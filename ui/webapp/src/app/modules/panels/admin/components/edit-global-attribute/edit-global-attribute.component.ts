import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Observable, of, Subscription } from 'rxjs';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { EditUniqueValueValidatorDirective } from '../../../../core/validators/edit-unique-value.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-edit-global-attribute',
    templateUrl: './edit-global-attribute.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
      BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        FormsModule,
        BdFormInputComponent,
        TrimmedValidator,
        EditUniqueValueValidatorDirective,
        BdButtonComponent,
        AsyncPipe,
    ],
})
export class EditGlobalAttributeComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected tempAttribute: CustomAttributeDescriptor;
  protected initialAttribute: CustomAttributeDescriptor;
  protected tempUsedIds: string[];
  protected loading$ = new BehaviorSubject<boolean>(true);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.settings.settings$]).subscribe(([route, settings]) => {
        if (!settings || !route?.params?.['attribute']) {
          return;
        }

        this.initialAttribute = settings.instanceGroup.attributes.find((a) => a.name === route.params['attribute']);
        this.tempAttribute = cloneDeep(this.initialAttribute);
        this.tempUsedIds = settings.instanceGroup.attributes
          .map((a) => a.name)
          .filter((a) => a !== this.initialAttribute.name);
        this.loading$.next(false);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public isDirty(): boolean {
    if (!this.tempAttribute) {
      return false;
    }
    return isDirty(this.tempAttribute, this.initialAttribute);
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  public doSave(): Observable<void> {
    return of(this.settings.editGlobalAttribute(this.tempAttribute, this.initialAttribute));
  }

  private reset() {
    this.tempAttribute = this.initialAttribute;
    this.areas.closePanel();
  }
}
