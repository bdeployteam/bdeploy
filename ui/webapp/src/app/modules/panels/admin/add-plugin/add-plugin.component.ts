import { Component, OnInit } from '@angular/core';
import { PluginAdminService } from 'src/app/modules/admin/services/plugin-admin.service';

@Component({
  selector: 'app-add-plugin',
  templateUrl: './add-plugin.component.html',
  styleUrls: ['./add-plugin.component.css'],
})
export class AddPluginComponent implements OnInit {
  /* template */ files: File[] = [];

  constructor(public plugins: PluginAdminService) {}

  ngOnInit(): void {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
