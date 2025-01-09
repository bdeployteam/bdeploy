import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export interface CrumbInfo {
  label: string;
  onClick: () => void;
}

@Component({
    selector: 'app-bd-breadcrumbs',
    templateUrl: './bd-breadcrumbs.component.html',
    styleUrls: ['./bd-breadcrumbs.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class BdBreadcrumbsComponent {
  @Input() crumbs: CrumbInfo[];

  protected onClick(crumb: CrumbInfo, last: boolean) {
    if (!last) {
      crumb.onClick();
    }
  }

  protected isMiddle(index: number): boolean {
    return index > 1 && index < this.crumbs.length - 2;
  }
}
