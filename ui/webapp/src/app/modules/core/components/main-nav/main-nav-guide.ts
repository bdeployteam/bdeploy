import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Guide, GuideService, GuideType } from '../../services/guide.service';
import { MainNavContentComponent } from '../main-nav-content/main-nav-content.component';
import { MainNavFlyinComponent } from '../main-nav-flyin/main-nav-flyin.component';
import { MainNavMenuComponent } from '../main-nav-menu/main-nav-menu.component';

@Component({ selector: 'app-main-nav-guide', template: '' })
export abstract class MainNavGuideComponent implements AfterViewInit {
  @ViewChild('navRoot', { read: ElementRef }) mainNav;
  @ViewChild(MainNavContentComponent, { read: ElementRef, static: false }) mainContent;
  @ViewChild(MainNavMenuComponent, { read: ElementRef, static: false }) mainMenu;
  @ViewChild(MainNavFlyinComponent, { read: ElementRef, static: false }) mainFlyin;

  constructor(private guides: GuideService, private router: Router) {}

  ngAfterViewInit(): void {
    const guide: Guide = {
      id: 'main-nav-user',
      elements: [
        {
          header: 'Welcome',
          content:
            'Welcome to BDeploy. This is the guided tour, which will explain important features of the user interface. <br/>' +
            '<strong>If you choose to skip guides, or disable the tour, you can reset this in your user settings.</strong>',
        },
        {
          element: this.mainMenu,
          header: 'Main Menu',
          content: 'This is the main menu. Menu items are added and removed depending on where you are in the application.',
        },
        {
          element: this.mainContent,
          header: 'Main Content Area',
          content: 'This is the main content area.',
        },
        {
          element: this.mainFlyin,
          header: 'Main Flyin Panel',
          content:
            'This is the so called flyin panel. It automatically pops in when required to display actions and details for the content of the main content area, as well ' +
            'as global theming and user related settings. Note that copying the URL from the address bar includes an open panel, so you can shared links ' +
            'to pages with a certain panel opened.',
          beforeElement: () => {
            this.router.navigate(['', { outlets: { panel: ['panels', 'user', 'themes'] } }]);
            return true;
          },
          afterElement: () => this.router.navigate(['', { outlets: { panel: null } }]),
        },
      ],
      type: GuideType.USER,
    };

    this.guides.register(guide);
  }
}
