import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { ACTION_APPLY, ACTION_CANCEL } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupDetailsService } from '../../../services/group-details.service';

@Component({
  selector: 'app-attribute-definitions',
  templateUrl: './attribute-definitions.component.html',
  styleUrls: ['./attribute-definitions.component.css'],
})
export class AttributeDefinitionsComponent implements OnInit {
  private defIdCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'id',
    name: 'ID',
    data: (r) => r.name,
  };

  private defDescCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'desc',
    name: 'Description',
    data: (r) => r.description,
  };

  private defDelCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove definition ${r.name}`,
    action: (r) => this.removeDefinition(r),
    icon: (r) => 'delete',
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<CustomAttributeDescriptor>[] = [this.defIdCol, this.defDescCol, this.defDelCol];

  /* template */ newId: string;
  /* template */ newDesc: string;

  constructor(public groups: GroupsService, public details: GroupDetailsService) {}

  ngOnInit(): void {}

  /* template */ showAddDialog(group: InstanceGroupConfiguration, templ: TemplateRef<any>) {
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
    group.instanceAttributes.splice(
      group.instanceAttributes.findIndex((r) => r === record),
      1
    );

    this.loading$.next(true);
    this.details
      .update(group)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }
}
