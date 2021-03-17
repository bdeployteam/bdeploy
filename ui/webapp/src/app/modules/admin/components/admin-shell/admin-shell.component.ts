import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-admin-shell',
  templateUrl: './admin-shell.component.html',
  styleUrls: ['./admin-shell.component.css'],
})
export class AdminShellComponent implements OnInit {
  constructor(private title: Title) {}

  ngOnInit() {
    // need to do manual, due to sub-routes, etc.
    this.title.setTitle('Administration');
  }
}
