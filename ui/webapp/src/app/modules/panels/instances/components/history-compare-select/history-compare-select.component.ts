import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceVersionDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';

@Component({
  selector: 'app-history-compare-select',
  templateUrl: './history-compare-select.component.html',
})
export class HistoryCompareSelectComponent implements OnInit, OnDestroy {
  private areas = inject(NavAreasService);
  private instances = inject(InstancesService);
  protected details = inject(HistoryDetailsService);

  private versionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'version',
    name: 'Instance Version',
    data: (r) => this.getVersionText(r),
    width: '100px',
    classes: (r) => this.getVersionClass(r),
  };

  private productVersionColumn: BdDataColumn<InstanceVersionDto> = {
    id: 'prodVersion',
    name: 'Product Version',
    data: (r) => r.product.tag,
  };

  private subscription: Subscription;
  private key: string;

  protected records$ = new BehaviorSubject<InstanceVersionDto[]>(null);
  protected base: string;
  protected columns: BdDataColumn<InstanceVersionDto>[] = [this.versionColumn, this.productVersionColumn];
  protected getRecordRoute = (row: InstanceVersionDto) => {
    if (row.key.tag === this.base) {
      return [];
    }
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'instances', 'history', this.key, 'compare', this.base, row.key.tag],
        },
      },
    ];
  };

  ngOnInit() {
    this.subscription = this.areas.panelRoute$.subscribe((r) => {
      if (!r) {
        return;
      }
      this.base = r.paramMap.get('base');
      this.key = r.paramMap.get('key');
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

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getVersionText(row: InstanceVersionDto) {
    if (row.key.tag === this.base) {
      return `${row.key.tag} - Selected`;
    }

    if (row.key.tag === this.instances.current$.value?.activeVersion?.tag) {
      return `${row.key.tag} - Active`;
    }

    if (row.key.tag === this.instances.current$.value?.instance?.tag) {
      return `${row.key.tag} - Current`;
    }

    return row.key.tag;
  }

  private getVersionClass(row: InstanceVersionDto): string[] {
    if (row.key.tag === this.base) {
      return ['bd-warning-text'];
    }

    if (row.key.tag === this.instances.current$.value?.activeVersion?.tag) {
      return ['bd-accent-text'];
    }

    if (row.key.tag === this.instances.current$.value?.instance?.tag) {
      return ['bd-accent-text'];
    }

    return [];
  }

  protected onBack() {
    window.history.back();
  }

  protected doSort(records: InstanceVersionDto[]) {
    return [...records].sort((a, b) => Number(b.key.tag) - Number(a.key.tag));
  }
}
