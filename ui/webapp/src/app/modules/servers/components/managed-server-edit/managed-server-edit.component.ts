import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-managed-server-edit',
  templateUrl: './managed-server-edit.component.html',
  styleUrls: ['./managed-server-edit.component.css']
})
export class ManagedServerEditComponent implements OnInit {

  constructor(@Inject(MAT_DIALOG_DATA) public server: ManagedMasterDto) { }

  ngOnInit(): void {
  }

}
