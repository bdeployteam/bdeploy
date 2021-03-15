import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceTemplateDescriptor } from 'src/app/models/gen.dtos';
import { ProductDetailsService } from '../../../../services/product-details.service';

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
  providers: [ProductDetailsService],
})
export class InstanceComponent implements OnInit {
  /* template */ columns: BdDataColumn<InstanceTemplateDescriptor>[] = [templateNameColumn, templateDescriptionColumn];

  constructor(public singleProduct: ProductDetailsService) {}

  ngOnInit(): void {}
}
