import { Component, OnInit } from '@angular/core';
import { ConfigService } from '../../services/config.service';
import { SearchService } from '../../services/search.service';

@Component({
  selector: 'app-main-nav-top',
  templateUrl: './main-nav-top.component.html',
  styleUrls: ['./main-nav-top.component.css'],
})
export class MainNavTopComponent implements OnInit {
  constructor(public cfgService: ConfigService, public search: SearchService) {}

  ngOnInit(): void {}
}
