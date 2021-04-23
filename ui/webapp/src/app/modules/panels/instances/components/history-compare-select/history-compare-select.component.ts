import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceVersionDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { HistoryDetailsService } from '../../services/history-details.service';

@Component({
  selector: 'app-history-compare-select',
  templateUrl: './history-compare-select.component.html',
  styleUrls: ['./history-compare-select.component.css'],
})
export class HistoryCompareSelectComponent implements OnInit, OnDestroy {
  private versionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'version',
    name: 'Instance Version',
    data: (r) => (r.key.tag === this.base ? `${r.key.tag} - Selected` : r.key.tag),
    width: '100px',
    classes: (r) => (r.key.tag === this.base ? ['bd-warning-text'] : []),
  };

  private productVersionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'prodVersion',
    name: 'Product Version',
    data: (r) => r.product.tag,
  };

  private subscription: Subscription;
  private index: string;

  /* template */ records$ = new BehaviorSubject<InstanceVersionDto[]>(null);
  /* template */ base: string;
  /* template */ columns: BdDataColumn<InstanceVersionDto>[] = [this.versionColumn, this.productVersionColumn];
  /* template */ getRecordRoute = (row: InstanceVersionDto) => {
    if (row.key.tag === this.base) {
      return [];
    }
    return ['', { outlets: { panel: ['panels', 'instances', 'history', this.index, 'compare', this.base, row.key.tag] } }];
  };

  constructor(private areas: NavAreasService, public details: HistoryDetailsService) {
    this.subscription = this.areas.panelRoute$.subscribe((r) => {
      if (!r) {
        return;
      }
      this.base = r.paramMap.get('base');
      this.index = r.paramMap.get('index');
    });

    this.subscription.add(
      this.details.versions$.subscribe((versions) => {
        if (!versions?.length) {
          this.records$.next(null);
        } else {
          this.records$.next(this.doSort(versions));
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onBack() {
    window.history.back();
  }

  /* template */ doSort(records: InstanceVersionDto[]) {
    return [...records].sort((a, b) => Number(b.key.tag) - Number(a.key.tag));
  }
}
