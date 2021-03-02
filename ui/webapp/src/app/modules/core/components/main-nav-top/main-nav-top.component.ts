import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit } from '@angular/core';
import { ConfigService } from '../../services/config.service';
import { SearchService } from '../../services/search.service';

@Component({
  selector: 'app-main-nav-top',
  templateUrl: './main-nav-top.component.html',
  styleUrls: ['./main-nav-top.component.css'],
})
export class MainNavTopComponent implements OnInit {
  /* template */ logoSize = 64;

  constructor(public cfgService: ConfigService, public search: SearchService, private media: BreakpointObserver) {
    this.media.observe('(max-width:1280px)').subscribe((bs) => {
      this.logoSize = bs.matches ? 48 : 64;
    });
  }

  ngOnInit(): void {}
}
