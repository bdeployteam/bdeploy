import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-attributes-tab',
  templateUrl: './attributes-tab.component.html',
  styleUrls: ['./attributes-tab.component.css'],
})
export class AttributesTabComponent implements OnInit {
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

  private defEditCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit definition ${r.name}`,
    action: (r) => this.editAttribute(r),
    icon: (r) => 'edit',
    width: '40px',
  };

  private defDelCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove definition ${r.name}`,
    action: (r) => this.settings.removeAttribute(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  /* template */ attributeColumns: BdDataColumn<CustomAttributeDescriptor>[] = [this.defIdCol, this.defDescCol, this.defEditCol, this.defDelCol];
  /* template */ tempAttribute: CustomAttributeDescriptor;
  /* template */ tempIsEdit: boolean;
  /* template */ tempUsedIds: string[];

  constructor(private router: Router, public settings: SettingsService) {}

  ngOnInit(): void {}

  /* template */ addAttribute(): void {
    this.router.navigate(['', { outlets: { panel: ['panels', 'admin', 'global-attribute-add'] } }]);
  }

  private editAttribute(attr: CustomAttributeDescriptor): void {
    this.router.navigate(['', { outlets: { panel: ['panels', 'admin', 'global-attribute', attr.name, 'edit'] } }]);
  }
}
