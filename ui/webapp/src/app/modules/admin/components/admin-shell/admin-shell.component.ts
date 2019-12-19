import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { HeaderTitleService } from 'src/app/modules/core/services/header-title.service';

@Component({
  selector: 'app-admin-shell',
  templateUrl: './admin-shell.component.html',
  styleUrls: ['./admin-shell.component.css']
})
export class AdminShellComponent implements OnInit {

  constructor(private header: HeaderTitleService, private title: Title) {}

  ngOnInit() {
    // need to do manual, due to sub-routes, etc.
    this.header.setHeaderTitle('Administration');
    this.title.setTitle('Administration');
  }

}
