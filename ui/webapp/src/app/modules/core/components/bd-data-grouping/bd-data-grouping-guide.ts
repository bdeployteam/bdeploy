import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { MatCard } from '@angular/material/card';
import { Guide, GuideService, GuideType } from '../../services/guide.service';
import { BdButtonPopupComponent } from '../bd-button-popup/bd-button-popup.component';

@Component({ selector: 'app-bd-data-grouping-guide', template: '' })
export abstract class BdDataGroupingGuideComponent implements AfterViewInit {
  @ViewChild(BdButtonPopupComponent, { read: ElementRef, static: false }) triggerButton;
  @ViewChild(BdButtonPopupComponent, { static: false }) triggerButtonRef;
  @ViewChild(MatCard, { read: ElementRef, static: false }) popupCard;

  constructor(private guides: GuideService) {}

  ngAfterViewInit(): void {
    const guide: Guide = {
      id: 'bd-data-grouping',
      elements: [
        {
          element: this.triggerButton,
          header: 'Data Grouping',
          content: 'Various views in BDeploy allow grouping. This is done through a common grouping panel.',
        },
        {
          element: this.popupCard,
          header: 'Data Grouping',
          content:
            'The grouping panel supports up to five levels of grouping. You can <em>pin</em> a grouping configuration so it will be the default for the current browser (stored locally in the browser).',
          beforeElement: () => {
            this.triggerButtonRef.openOverlay();
            return true;
          },
          afterElement: () => {
            this.triggerButtonRef.closeOverlay();
          },
        },
      ],
      type: GuideType.USER,
    };

    this.guides.register(guide);
  }
}
