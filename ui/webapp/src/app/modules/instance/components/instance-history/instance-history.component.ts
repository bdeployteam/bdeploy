import { Location } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import {
  HistoryEntryDto,
  InstanceConfiguration
} from 'src/app/models/gen.dtos';
import { LoggingService } from 'src/app/modules/core/services/logging.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { InstanceHistoryTimelineComponent } from 'src/app/modules/instance/components/instance-history-timeline/instance-history-timeline.component';
import { InstanceService } from '../../services/instance.service';
import { InstanceHistoryCompareComponent } from '../instance-history-compare/instance-history-compare.component';

@Component({
  selector: 'app-instance-history',
  templateUrl: './instance-history.component.html',
  styleUrls: ['./instance-history.component.css'],
})
export class InstanceHistoryComponent implements OnInit {
  // Amount of history entries to load at once
  private readonly MAX_RESULTS = 50;

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');

  loading = true;

  filterText = '';
  showCreate = true;
  showDeployment = false;
  showRuntime = false;

  accordionBehaviour = false;

  private compareVersions: string[] = [];

  instance: InstanceConfiguration;
  history: HistoryEntryDto[] = [];
  nextInstanceTag: string = null;
  allLoaded = false;

  @ViewChild('searchInput')
  searchInput: ElementRef<HTMLInputElement>;

  @ViewChild('timeline')
  timeline: InstanceHistoryTimelineComponent;

  @ViewChild('compareButton')
  compareButton: MatButton;

  @ViewChild('compareA')
  compareInputA: ElementRef<HTMLInputElement>;

  @ViewChild('compareB')
  compareInputB: ElementRef<HTMLInputElement>;

  constructor(
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    public location: Location,
    private loggingService: LoggingService,
    public routingHistoryService: RoutingHistoryService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.instanceService
      .getInstance(this.groupParam, this.uuidParam)
      .subscribe((val) => (this.instance = val));
    this.loadHistory();
  }

  closeAll(): void {
    this.timeline.closeAll();
  }

  private loadHistory(): void {
    this.loading = true;
    this.instanceService
      .getInstanceHistory(
        this.groupParam,
        this.uuidParam,
        this.MAX_RESULTS,
        this.nextInstanceTag,
        this.filterText,
        this.showCreate,
        this.showDeployment,
        this.showRuntime
      )
      .subscribe((r) => {
        this.history = this.history.concat(r.events);
        this.nextInstanceTag = r.next;
        this.allLoaded = r.next == null;
        this.loading = false;
        if (r.errors.length > 0) {
          this.loggingService.guiError(r.errors.join('\n'));
        }
      });
  }

  onScrolledDown(): void {
    if (this.allLoaded) {
      return;
    }
    this.loadHistory();
  }

  compareInputKeydown(event: InputEvent, index: number): void {
    const input = event.target as HTMLInputElement;
    const value = input.value.trim();
    if (this.isNumeric(value)) {
      this.compareVersions[index] = value;
    } else {
      this.compareVersions[index] = null;
    }
    this.updateCompareButton();
  }

  updateCompareButton() {
    if (!this.compareVersions[0] || !this.compareVersions[1]) {
      this.compareButton.disabled = true;
    } else if (this.compareVersions[0] === this.compareVersions[1]) {
      this.compareButton.disabled = true;
    } else {
      this.compareButton.disabled = false;
    }
  }

  isNumeric(number: string): boolean {
    return !isNaN(parseInt(number, 10));
  }

  addVersionToCompare(version: string) {
    const x = Number(version);

    // push first value to second value
    this.compareVersions[1] = this.compareVersions[0];

    // set first value to new value
    this.compareVersions[0] = version;

    this.compareInputA.nativeElement.value = this.compareVersions[0] || '';
    this.compareInputB.nativeElement.value = this.compareVersions[1] || '';
    this.updateCompareButton();
  }

  compareVersion(): void {
    const data = [
      this.instanceService,
      this.groupParam,
      this.uuidParam,
      this.compareVersions,
    ];
    this.dialog.open(InstanceHistoryCompareComponent, {
      minWidth: '300px',
      maxWidth: '800px',
      data: data,
      closeOnNavigation: true,
    });
  }

  onTypeFilterChanged() {
    this.resetHistory();
    this.loadHistory();
  }

  onTextFilterChanged() {
    this.resetHistory();
    this.loadHistory();
  }

  resetHistory() {
    this.history = [];
    this.allLoaded = false;
    this.nextInstanceTag = null;
  }
}
