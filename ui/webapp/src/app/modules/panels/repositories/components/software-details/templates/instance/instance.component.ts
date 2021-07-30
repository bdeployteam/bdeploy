import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceTemplateDescriptor } from 'src/app/models/gen.dtos';
import { SoftwareDetailsService } from '../../../../services/software-details.service';

const templateNameColumn: BdDataColumn<InstanceTemplateDescriptor> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '140px',
};

const templateDescriptionColumn: BdDataColumn<InstanceTemplateDescriptor> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
  width: '140px',
};

@Component({
  selector: 'app-instance',
  templateUrl: './instance.component.html',
  styleUrls: ['./instance.component.css'],
  providers: [SoftwareDetailsService],
})
export class InstanceComponent implements OnInit {
  /* template */ columns: BdDataColumn<InstanceTemplateDescriptor>[] = [templateNameColumn, templateDescriptionColumn];

  constructor(public details: SoftwareDetailsService) {}

  ngOnInit(): void {}
}
