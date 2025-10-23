import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { JerseyServerMonitoringDto, MetricBundle, MetricGroup, TimerMetric } from '../../../../../models/gen.dtos';
import { MetricsService } from '../../services/metrics.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatTab, MatTabGroup, MatTabLabel } from '@angular/material/tabs';
import { BarChartModule, LineChartModule } from '@swimlane/ngx-charts';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';
import { AsyncPipe } from '@angular/common';

export interface SeriesElement {
  name: string;
  value: number;
}

export interface Series {
  name: string;
  series: SeriesElement[];
}

export interface TimestampedElement {
  name: Date;
  value: number;
}

export interface ChronologicalSeries {
  name: string;
  series: TimestampedElement[];
}

export interface HistogramDetails {
  min: number;
  max: number;
  median: number;
  p75th: number;
  p99th: number;
}

@Component({
    selector: 'app-metrics-overview',
    templateUrl: './metrics-overview.component.html',
    styleUrls: ['./metrics-overview.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatTabGroup, MatTab, MatTabLabel, LineChartModule, MatIcon, BarChartModule, MatDivider, AsyncPipe]
})
export class MetricsOverviewComponent implements OnInit {
  private readonly metrics = inject(MetricsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private allMetrics: Map<MetricGroup, MetricBundle>;

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected keys$ = new BehaviorSubject<MetricGroup[]|string[]>(['SERVER']);
  protected tabIndex: number;

  protected selectedGroup: MetricGroup;
  protected groupCounts: SeriesElement[];
  protected selectedTimer: TimerMetric;
  protected selectedTimerName: string;
  protected histogramDetails: HistogramDetails;
  protected serverStats: JerseyServerMonitoringDto;

  // converted data for serverstats
  protected vmCpu: ChronologicalSeries[] = [];
  protected vmCpuRef: SeriesElement[] = [];
  protected vmMem: ChronologicalSeries[] = [];
  protected vmMemRef: SeriesElement[] = [];
  protected req: ChronologicalSeries[] = [];
  protected reqAbs: ChronologicalSeries[] = [];
  protected poolSize: ChronologicalSeries[] = [];
  protected poolSizeRef: SeriesElement[] = [];
  protected poolTasks: ChronologicalSeries[] = [];
  protected conBytes: ChronologicalSeries[] = [];
  protected conBytesAbs: ChronologicalSeries[] = [];
  protected activeSess: ChronologicalSeries[] = [];

  protected timerSeries: Series[];
  protected referenceLines: SeriesElement[];

  protected countGraphHeight = 100;

  ngOnInit() {
    this.metrics
      .getAllMetrics()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((record: Record<MetricGroup, MetricBundle>) => {
        this.allMetrics = new Map<MetricGroup, MetricBundle>();
        for (const metricEntry of Object.entries(record)) {
          this.allMetrics.set(metricEntry[0] as MetricGroup, metricEntry[1]);
        }
        this.keys$.next(this.keys$.value.concat(Array.from(this.allMetrics.keys())));
        const tabIndex = Number.parseInt(this.route.snapshot.queryParamMap.get('tabIndex'), 10);
        this.doSelect(isNaN(tabIndex) ? 0 : tabIndex);
      });
  }

  private getTimers(group: MetricGroup): string[] {
    return Object.keys(this.allMetrics.get(group).timers);
  }

  private getTimer(group: MetricGroup, name: string): TimerMetric {
    return this.allMetrics.get(group).timers[name];
  }

  protected doSelect(tabIndex: number) {
    this.router.navigate([], { queryParams: { tabIndex } });
    this.tabIndex = tabIndex;
    if(this.tabIndex) {
      this.select(this.keys$.value[this.tabIndex])
    } else {
      this.selectServer()
    }
  }

  private selectServer() {
    this.selectedTimer = null;
    this.selectedTimerName = null;
    this.histogramDetails = null;
    this.timerSeries = null;
    this.referenceLines = null;
    this.selectedGroup = null;

    this.vmCpu = [];
    this.vmCpuRef = [];
    this.vmMem = [];
    this.vmMemRef = [];
    this.req = [];
    this.reqAbs = [];
    this.poolSize = [];
    this.poolSizeRef = [];
    this.poolTasks = [];
    this.conBytes = [];
    this.conBytesAbs = [];
    this.activeSess = [];

    this.metrics.getServerMetrics().subscribe((r) => {
      this.serverStats = r;

      // calculate series for monitoring graphs.
      const vmCpuThreadCount: TimestampedElement[] = [];
      let vmCpuCount = 0;
      let vmMemMax = 0;

      const vmMemTotal: TimestampedElement[] = [];
      const vmMemUsed: TimestampedElement[] = [];

      const reqReceived: TimestampedElement[] = [];
      const reqCompleted: TimestampedElement[] = [];
      const reqTimedOut: TimestampedElement[] = [];
      const reqCancelled: TimestampedElement[] = [];

      const reqReceivedAbs: TimestampedElement[] = [];
      const reqCompletedAbs: TimestampedElement[] = [];
      const reqTimedOutAbs: TimestampedElement[] = [];
      const reqCancelledAbs: TimestampedElement[] = [];

      let lastReqReceived = this.serverStats?.snapshots[0]?.reqReceived;
      let lastReqCompleted = this.serverStats?.snapshots[0]?.reqCompleted;
      let lastReqTimedOut = this.serverStats?.snapshots[0]?.reqTimedOut;
      let lastReqCancelled = this.serverStats?.snapshots[0]?.reqCancelled;

      let poolCoreSize = 0;
      let poolMaxSize = 0;
      let poolHighestCurrent = 0;
      const poolCurrentSize: TimestampedElement[] = [];
      const poolExceeded: TimestampedElement[] = [];
      let lastPoolExceeded = this.serverStats?.snapshots[0]?.poolExceeded;

      const poolTasksQueued: TimestampedElement[] = [];
      const poolTasksFinished: TimestampedElement[] = [];
      const poolTasksCancelled: TimestampedElement[] = [];

      let lastTasksQueued = this.serverStats?.snapshots[0]?.poolTasksQueued;
      let lastTasksFinished = this.serverStats?.snapshots[0]?.poolTasksFinished;
      let lastTasksCancelled = this.serverStats?.snapshots[0]?.poolTasksCancelled;

      const conBytesRead: TimestampedElement[] = [];
      const conBytesWritten: TimestampedElement[] = [];

      const conBytesReadAbs: TimestampedElement[] = [];
      const conBytesWrittenAbs: TimestampedElement[] = [];

      let lastBytesRead = this.serverStats?.snapshots[0]?.conBytesRead;
      let lastBytesWritten = this.serverStats?.snapshots[0]?.conBytesWritten;

      const activeSessions: TimestampedElement[] = [];

      for (const snap of this.serverStats.snapshots) {
        if (vmCpuCount === 0) {
          vmCpuCount = snap.vmCpus;
        }
        if (vmMemMax === 0) {
          vmMemMax = snap.vmMaxMem;
        }
        if (snap.vmCpus !== vmCpuCount) {
          vmCpuCount = snap.vmCpus;
          console.warn('Server CPU count changed!');
        }
        if (snap.vmMaxMem !== vmMemMax) {
          vmMemMax = snap.vmMaxMem;
          console.warn('Server Maximum Memory changed!');
        }

        const label = new Date(snap.snapshotTime);

        vmCpuThreadCount.push({ name: label, value: snap.vmThreads });
        vmMemTotal.push({
          name: label,
          value: snap.vmTotalMem / (1024 * 1024),
        });
        vmMemUsed.push({
          name: label,
          value: (snap.vmTotalMem - snap.vmFreeMem) / (1024 * 1024),
        });

        reqCompleted.push({
          name: label,
          value: snap.reqCompleted - lastReqCompleted,
        });
        reqReceived.push({
          name: label,
          value: snap.reqReceived - lastReqReceived,
        });
        reqCancelled.push({
          name: label,
          value: snap.reqCancelled - lastReqCancelled,
        });
        reqTimedOut.push({
          name: label,
          value: snap.reqTimedOut - lastReqTimedOut,
        });

        lastReqCompleted = snap.reqCompleted;
        lastReqReceived = snap.reqReceived;
        lastReqCancelled = snap.reqCancelled;
        lastReqTimedOut = snap.reqTimedOut;

        reqCompletedAbs.push({ name: label, value: snap.reqCompleted });
        reqReceivedAbs.push({ name: label, value: snap.reqReceived });
        reqCancelledAbs.push({ name: label, value: snap.reqCancelled + 3 });
        reqTimedOutAbs.push({ name: label, value: snap.reqTimedOut + 6 });

        poolCoreSize = snap.poolCoreSize;
        poolMaxSize = snap.poolMaxSize;
        poolCurrentSize.push({ name: label, value: snap.poolCurrentSize });
        poolExceeded.push({
          name: label,
          value: snap.poolExceeded - lastPoolExceeded,
        });
        lastPoolExceeded = snap.poolExceeded;
        if (snap.poolCurrentSize > poolHighestCurrent) {
          poolHighestCurrent = snap.poolCurrentSize;
        }

        poolTasksQueued.push({
          name: label,
          value: snap.poolTasksQueued - lastTasksQueued,
        });
        poolTasksFinished.push({
          name: label,
          value: snap.poolTasksFinished - lastTasksFinished,
        });
        poolTasksCancelled.push({
          name: label,
          value: snap.poolTasksCancelled - lastTasksCancelled,
        });

        lastTasksQueued = snap.poolTasksQueued;
        lastTasksFinished = snap.poolTasksFinished;
        lastTasksCancelled = snap.poolTasksCancelled;

        conBytesRead.push({
          name: label,
          value: snap.conBytesRead - lastBytesRead,
        });
        conBytesWritten.push({
          name: label,
          value: snap.conBytesWritten - lastBytesWritten,
        });

        lastBytesRead = snap.conBytesRead;
        lastBytesWritten = snap.conBytesWritten;

        conBytesReadAbs.push({
          name: label,
          value: snap.conBytesRead / (1024 * 1024),
        });
        conBytesWrittenAbs.push({
          name: label,
          value: snap.conBytesWritten / (1024 * 1024),
        });

        activeSessions.push({
          name: label,
          value: snap.activeSessions,
        });
      }

      this.vmCpu.push({ name: 'Threads', series: vmCpuThreadCount });
      this.vmCpuRef.push({ name: 'CPU Count', value: vmCpuCount });

      this.vmMem.push({ name: 'Total Memory MB', series: vmMemTotal });
      this.vmMem.push({ name: 'Used Memory MB', series: vmMemUsed });
      this.vmMemRef.push({
        name: 'Max Memory MB',
        value: vmMemMax / (1024 * 1024),
      });

      this.req.push({ name: 'Received', series: reqReceived });
      this.req.push({ name: 'Completed', series: reqCompleted });
      this.req.push({ name: 'Cancelled', series: reqCancelled });
      this.req.push({ name: 'Timed Out', series: reqTimedOut });

      this.reqAbs.push({ name: 'Received', series: reqReceivedAbs });
      this.reqAbs.push({ name: 'Completed', series: reqCompletedAbs });
      this.reqAbs.push({ name: 'Cancelled', series: reqCancelledAbs });
      this.reqAbs.push({ name: 'Timed Out', series: reqTimedOutAbs });

      this.poolSize.push({ name: 'Current Size', series: poolCurrentSize });
      this.poolSize.push({
        name: 'Times Limit Exceeded',
        series: poolExceeded,
      });
      this.poolSizeRef.push({ name: 'Core Size', value: poolCoreSize });
      if (poolHighestCurrent * 2 >= poolMaxSize) {
        this.poolSizeRef.push({
          name: 'Maximum Pool Size',
          value: poolMaxSize,
        });
      }

      this.poolTasks.push({ name: 'Queued', series: poolTasksQueued });
      this.poolTasks.push({ name: 'Finished', series: poolTasksFinished });
      this.poolTasks.push({ name: 'Cancelled', series: poolTasksCancelled });

      this.conBytes.push({ name: 'Read', series: conBytesRead });
      this.conBytes.push({ name: 'Written', series: conBytesWritten });

      this.conBytesAbs.push({ name: 'Read', series: conBytesReadAbs });
      this.conBytesAbs.push({ name: 'Written', series: conBytesWrittenAbs });

      this.activeSess.push({ name: 'Active Sessions [5m]', series: activeSessions });
    });
  }

  private select(selectedGroup: MetricGroup | string) {
    this.serverStats = null;
    this.selectedTimer = null;
    this.selectedTimerName = null;
    this.histogramDetails = null;
    this.timerSeries = null;
    this.referenceLines = null;

    this.selectedGroup = MetricGroup[selectedGroup as keyof typeof MetricGroup];

    const x: SeriesElement[] = [];

    for (const t of this.getTimers(this.selectedGroup)) {
      const tm = this.getTimer(this.selectedGroup, t);

      x.push({
        name: t,
        value: tm.counter.value,
      });
    }

    // sort the timers...
    x.sort((a, b) => {
      const ta = this.getTimer(this.selectedGroup, a.name);
      const tb = this.getTimer(this.selectedGroup, b.name);

      return tb.histogram.median - ta.histogram.median;
    });

    this.countGraphHeight = x.length * 25 + 100;
    this.groupCounts = x;
  }

  protected selectTimer(t: SeriesElement) {
    this.timerSeries = [];
    const timer = this.getTimer(this.selectedGroup, t.name);

    let cnt = 0;
    const points: SeriesElement[] = [];
    const data = Array.from(timer.histogram.values).reverse();
    for (const x of data) {
      points.push({
        name: `${cnt++}`,
        value: this.toMillis(x),
      });
    }
    this.timerSeries.push({
      name: t.name,
      series: points,
    });

    this.referenceLines = [
      { name: 'Mean', value: this.toMillis(timer.histogram.mean) },
      { name: 'Median', value: this.toMillis(timer.histogram.median) },
      { name: '99th percentile', value: this.toMillis(timer.histogram.p99th) },
      { name: '75th percentile', value: this.toMillis(timer.histogram.p75th) },
    ];

    this.selectedTimer = timer;
    this.selectedTimerName = t.name;
    this.histogramDetails = {
      median: this.toMillis(this.selectedTimer.histogram.median),
      p75th: this.toMillis(this.selectedTimer.histogram.p75th),
      p99th: this.toMillis(this.selectedTimer.histogram.p99th),
      min: this.toMillis(this.selectedTimer.histogram.min),
      max: this.toMillis(this.selectedTimer.histogram.max),
    };
  }

  private toMillis(nanos: number): number {
    // nanos to millis and round to 2 decimal places
    return Math.round(nanos / 10000) / 100;
  }
}
