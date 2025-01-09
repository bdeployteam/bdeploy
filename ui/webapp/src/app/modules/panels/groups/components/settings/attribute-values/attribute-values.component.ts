import { Component, inject, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor, CustomAttributesRecord, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import {
  ACTION_APPLY,
  ACTION_CANCEL
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupDetailsService } from '../../../services/group-details.service';
import { BdFormSelectComponent } from '../../../../../core/components/bd-form-select/bd-form-select.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';

import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../../core/components/bd-no-data/bd-no-data.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';

interface AttributeRow {
  id: string;
  name: string;
  value: string;
}

@Component({
    selector: 'app-attribute-values',
    templateUrl: './attribute-values.component.html',
    imports: [
        BdFormSelectComponent,
        FormsModule,
        BdFormInputComponent,
      BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        BdDataDisplayComponent,
        BdNoDataComponent,
        BdButtonComponent,
    ],
})
export class AttributeValuesComponent implements OnInit, OnDestroy {
  private readonly groups = inject(GroupsService);
  private readonly details = inject(GroupDetailsService);

  private readonly attrNameCol: BdDataColumn<AttributeRow> = {
    id: 'attribute',
    name: 'Attribute',
    data: (r) => r.name,
    isId: true,
  };

  private readonly attrValCol: BdDataColumn<AttributeRow> = {
    id: 'value',
    name: 'Value',
    data: (r) => r.value,
  };

  private readonly attrRemoveCol: BdDataColumn<AttributeRow> = {
    id: 'remove',
    name: 'Rem.',
    data: (r) => `Remove value for ${r.name}`,
    icon: () => 'delete',
    action: (r) => this.removeAttribute(r),
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected readonly columns: BdDataColumn<AttributeRow>[] = [this.attrNameCol, this.attrValCol, this.attrRemoveCol];
  protected records: AttributeRow[] = [];
  protected defs: CustomAttributeDescriptor[];

  protected newAttr: CustomAttributeDescriptor;
  protected newValue: string;
  protected defLabels: string[];

  private group: InstanceGroupConfiguration;
  private attributes: CustomAttributesRecord;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.groups.current$,
      this.groups.attributeDefinitions$,
      this.groups.currentAttributeValues$,
    ]).subscribe((r) => {
      this.group = r[0];
      this.defs = r[1];
      this.attributes = r[2];
      this.defLabels = this.defs.map((d) => d.description);

      // if we have values for both
      this.createRows();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected showAddDialog(template: TemplateRef<unknown>) {
    this.dialog
      .message({
        header: 'Set Attribute Value',
        icon: 'edit',
        template: template,
        validation: () => !!this.newAttr && !!this.newValue?.length,
        actions: [ACTION_CANCEL, ACTION_APPLY],
      })
      .subscribe((r) => {
        const attr = this.newAttr;
        const value = this.newValue;
        this.newAttr = this.newValue = null;

        if (!r) {
          return;
        }

        this.attributes.attributes[attr.name] = value;

        this.loading$.next(true);
        this.details
          .updateAttributes(this.group.name, this.attributes)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe();
      });
  }

  private removeAttribute(r: AttributeRow) {
    delete this.attributes.attributes[r.id];
    this.loading$.next(true);
    this.details
      .updateAttributes(this.group.name, this.attributes)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  private createRows() {
    if (!this.attributes?.attributes) {
      this.attributes = { attributes: {} };
    }

    if (!this.defs?.length) {
      this.records = [];
    } else {
      const result: AttributeRow[] = [];
      for (const def of this.defs) {
        if (this.attributes.attributes[def.name]) {
          result.push({
            id: def.name,
            name: def.description,
            value: this.attributes.attributes[def.name],
          });
        }
      }
      this.records = result;
    }
  }
}
