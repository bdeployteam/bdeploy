import { Component, inject, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import {
  ACTION_APPLY,
  ACTION_CANCEL,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupDetailsService } from '../../../services/group-details.service';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';
import { TrimmedValidator } from '../../../../../core/validators/trimmed.directive';
import { EditUniqueValueValidatorDirective } from '../../../../../core/validators/edit-unique-value.directive';

import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../../core/components/bd-no-data/bd-no-data.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-attribute-definitions',
  templateUrl: './attribute-definitions.component.html',
  imports: [
    BdFormInputComponent,
    FormsModule,
    TrimmedValidator,
    EditUniqueValueValidatorDirective,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdDataDisplayComponent,
    BdNoDataComponent,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class AttributeDefinitionsComponent {
  protected readonly groups = inject(GroupsService);
  protected readonly details = inject(GroupDetailsService);

  private readonly defIdCol: BdDataColumn<CustomAttributeDescriptor, string> = {
    id: 'id',
    name: 'ID',
    data: (r) => r.name,
    isId: true,
  };

  private readonly defDescCol: BdDataColumn<CustomAttributeDescriptor, string> = {
    id: 'desc',
    name: 'Description',
    data: (r) => r.description,
  };

  private readonly defDelCol: BdDataColumn<CustomAttributeDescriptor, string> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove definition ${r.name}`,
    action: (r) => this.removeDefinition(r),
    icon: () => 'delete',
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected readonly columns: BdDataColumn<CustomAttributeDescriptor, unknown>[] = [
    this.defIdCol,
    this.defDescCol,
    this.defDelCol,
  ];

  protected newId: string;
  protected newDesc: string;
  protected newUsedIds: string[];

  protected showAddDialog(group: InstanceGroupConfiguration, templ: TemplateRef<unknown>) {
    this.newUsedIds = group.instanceAttributes.map((r) => r.name);
    this.dialog
      .message({
        header: 'Add Definition',
        icon: 'add',
        template: templ,
        validation: () => !!this.newId?.length && !!this.newDesc?.length,
        actions: [ACTION_CANCEL, ACTION_APPLY],
      })
      .subscribe((r) => {
        const id = this.newId;
        const desc = this.newDesc;
        this.newId = this.newDesc = null;

        if (!r) {
          return;
        }

        group.instanceAttributes.push({ name: id, description: desc });

        this.loading$.next(true);
        this.details
          .update(group)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe();
      });
  }

  private removeDefinition(record: CustomAttributeDescriptor) {
    const group = this.groups.current$.value; // this is the same as used in the template, so it must be valid.
    group.instanceAttributes.splice(group.instanceAttributes.indexOf(record), 1);

    this.loading$.next(true);
    this.details
      .update(group)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }
}
