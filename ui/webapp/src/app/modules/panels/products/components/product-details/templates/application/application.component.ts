import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationTemplateDescriptor } from 'src/app/models/gen.dtos';
import { ProductDetailsService } from '../../../../services/product-details.service';

const templateNameColumn: BdDataColumn<ApplicationTemplateDescriptor> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '140px',
};

const templateDescriptionColumn: BdDataColumn<ApplicationTemplateDescriptor> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
  width: '140px',
};

@Component({
  selector: 'app-application',
  templateUrl: './application.component.html',
  styleUrls: ['./application.component.css'],
  providers: [ProductDetailsService],
})
export class ApplicationComponent implements OnInit {
  /* template */ columns: BdDataColumn<ApplicationTemplateDescriptor>[] = [templateNameColumn, templateDescriptionColumn];

  constructor(public singleProduct: ProductDetailsService) {}

  ngOnInit(): void {}
}
