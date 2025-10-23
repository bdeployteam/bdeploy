import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceVersionDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-history-compare-select',
  templateUrl: './history-compare-select.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdDataTableComponent, AsyncPipe],
})
export class HistoryCompareSelectComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly instances = inject(InstancesService);
  protected readonly details = inject(HistoryDetailsService);

  private readonly versionColumn: BdDataColumn<InstanceVersionDto, string> = {
    id: 'version',
    name: 'Instance Version',
    data: (r) => this.getVersionText(r),
    width: '100px',
    classes: (r) => this.getVersionClass(r),
  };

  private readonly productVersionColumn: BdDataColumn<InstanceVersionDto, string> = {
    id: 'prodVersion',
    name: 'Product Version',
    data: (r) => r.product.tag,
  };

  private subscription: Subscription;
  private key: string;

  protected records$ = new BehaviorSubject<InstanceVersionDto[]>(null);
  protected base: string;
  protected columns: BdDataColumn<InstanceVersionDto, unknown>[] = [this.versionColumn, this.productVersionColumn];
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
    globalThis.history.back();
  }

  protected doSort(records: InstanceVersionDto[]) {
    return [...records].sort((a, b) => Number(b.key.tag) - Number(a.key.tag));
  }
}
