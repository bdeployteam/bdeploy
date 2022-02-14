import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
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
export class AddGlobalAttributeComponent
  implements OnInit, OnDestroy, DirtyableDialog
{
  /* template */ tempAttribute: CustomAttributeDescriptor;
  /* template */ tempUsedIds: string[];
  /* template */ saving$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  constructor(
    private settings: SettingsService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.tempAttribute = { name: '', description: '' };
    this.tempUsedIds =
      this.settings.settings$.value.instanceGroup.attributes.map((a) => a.name);
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        })
      )
      .subscribe(() => {
        this.areas.closePanel();
        this.subscription.unsubscribe();
      });
  }

  public doSave(): Observable<boolean> {
    this.saving$.next(true);
    return this.settings.addGlobalAttribute(this.tempAttribute);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
