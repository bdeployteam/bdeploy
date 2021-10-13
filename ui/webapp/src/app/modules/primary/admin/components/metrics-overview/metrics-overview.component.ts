import { Component, OnInit, ViewChild } from '@angular/core';
import { BarHorizontalComponent } from '@swimlane/ngx-charts';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { JerseyServerMonitoringDto, MetricBundle, MetricGroup, TimerMetric } from '../../../../../models/gen.dtos';
import { MetricsService } from '../../services/metrics.service';

export interface SeriesElement {
  name: any;
  value: number;
}

@Component({
  selector: 'app-metrics-overview',
  templateUrl: './metrics-overview.component.html',
  styleUrls: ['./metrics-overview.component.css'],
})
export class MetricsOverviewComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ keys$ = new BehaviorSubject<string[]>(['SERVER']);

  private readonly log: Logger = this.loggingService.getLogger('MetricsOverviewComponent');

  selection: string;
  allMetrics: Map<MetricGroup, MetricBundle>;
  selectedGroup: MetricGroup;
  groupCounts: SeriesElement[];
  selectedTimer: TimerMetric;
  selectedTimerName: string;
  serverStats: JerseyServerMonitoringDto;

  // converted data for serverstats
  vmCpu = [];
  vmCpuRef = [];
  vmMem = [];
  vmMemRef = [];
  req = [];
  reqAbs = [];
  poolSize = [];
  poolSizeRef = [];
  poolTasks = [];
  conBytes = [];
  conBytesAbs = [];

  colorScheme = {
    domain: ['#5AA454', '#A10A28', '#C7B42C', '#AAAAAA'],
  };
  timerSeries: { name: string; series: SeriesElement[] }[];
  referenceLines: SeriesElement[];

  @ViewChild('countChart')
  countChart: BarHorizontalComponent;

  countGraphHeight = 100;

  constructor(private metrics: MetricsService, private loggingService: LoggingService) {}

  ngOnInit() {
    this.metrics
      .getAllMetrics()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => {
        // result is an object with a property per MetricGroup
        this.allMetrics = new Map<MetricGroup, MetricBundle>();
        for (const prop of Object.keys(r)) {
          const group = prop as MetricGroup;
          const item = r[prop] as MetricBundle;

          this.allMetrics.set(group, item);
        }
        this.keys$.next([...Array.from(this.allMetrics.keys()), 'SERVER']);
      });
  }

  getGroup(group: MetricGroup): MetricBundle {
    return this.allMetrics.get(group);
  }

  getTimers(group: MetricGroup): string[] {
    return Object.keys(this.allMetrics.get(group).timers);
  }

  getTimer(group: MetricGroup, name: string): TimerMetric {
    return this.allMetrics.get(group).timers[name];
  }

  doSelect() {
    if (this.selection === 'SERVER') {
      this.selectServer();
    } else {
      this.select();
    }
  }

  private selectServer() {
    this.selectedTimer = null;
    this.selectedTimerName = null;
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

    this.metrics.getServerMetrics().subscribe((r) => {
      this.serverStats = r;

      // calculate series for monitoring graphs.
      const vmCpuThreadCount: SeriesElement[] = [];
      let vmCpuCount = 0;
      let vmMemMax = 0;

      const vmMemTotal: SeriesElement[] = [];
      const vmMemUsed: SeriesElement[] = [];

      const reqReceived: SeriesElement[] = [];
      const reqCompleted: SeriesElement[] = [];
      const reqTimedOut: SeriesElement[] = [];
      const reqCancelled: SeriesElement[] = [];

      const reqReceivedAbs: SeriesElement[] = [];
      const reqCompletedAbs: SeriesElement[] = [];
      const reqTimedOutAbs: SeriesElement[] = [];
      const reqCancelledAbs: SeriesElement[] = [];

      let lastReqReceived = this.serverStats?.snapshots[0]?.reqReceived;
      let lastReqCompleted = this.serverStats?.snapshots[0]?.reqCompleted;
      let lastReqTimedOut = this.serverStats?.snapshots[0]?.reqTimedOut;
      let lastReqCancelled = this.serverStats?.snapshots[0]?.reqCancelled;

      let poolCoreSize = 0;
      let poolMaxSize = 0;
      let poolHighestCurrent = 0;
      const poolCurrentSize: SeriesElement[] = [];
      const poolExceeded: SeriesElement[] = [];
      let lastPoolExceeded = this.serverStats?.snapshots[0]?.poolExceeded;

      const poolTasksQueued: SeriesElement[] = [];
      const poolTasksFinished: SeriesElement[] = [];
      const poolTasksCancelled: SeriesElement[] = [];

      let lastTasksQueued = this.serverStats?.snapshots[0]?.poolTasksQueued;
      let lastTasksFinished = this.serverStats?.snapshots[0]?.poolTasksFinished;
      let lastTasksCancelled = this.serverStats?.snapshots[0]?.poolTasksCancelled;

      const conBytesRead: SeriesElement[] = [];
      const conBytesWritten: SeriesElement[] = [];

      const conBytesReadAbs: SeriesElement[] = [];
      const conBytesWrittenAbs: SeriesElement[] = [];

      let lastBytesRead = this.serverStats?.snapshots[0]?.conBytesRead;
      let lastBytesWritten = this.serverStats?.snapshots[0]?.conBytesWritten;

      for (const snap of this.serverStats.snapshots) {
        if (vmCpuCount === 0) {
          vmCpuCount = snap.vmCpus;
        }
        if (vmMemMax === 0) {
          vmMemMax = snap.vmMaxMem;
        }
        if (snap.vmCpus !== vmCpuCount) {
          vmCpuCount = snap.vmCpus;
          this.log.warn('Server CPU count changed!');
        }
        if (snap.vmMaxMem !== vmMemMax) {
          vmMemMax = snap.vmMaxMem;
          this.log.warn('Server Maximum Memory changed!');
        }

        const label = new Date(snap.snapshotTime);

        vmCpuThreadCount.push({ name: label, value: snap.vmThreads });
        vmMemTotal.push({ name: label, value: snap.vmTotalMem / (1024 * 1024) });
        vmMemUsed.push({ name: label, value: (snap.vmTotalMem - snap.vmFreeMem) / (1024 * 1024) });

        reqCompleted.push({ name: label, value: snap.reqCompleted - lastReqCompleted });
        reqReceived.push({ name: label, value: snap.reqReceived - lastReqReceived });
        reqCancelled.push({ name: label, value: snap.reqCancelled - lastReqCancelled });
        reqTimedOut.push({ name: label, value: snap.reqTimedOut - lastReqTimedOut });

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
        poolExceeded.push({ name: label, value: snap.poolExceeded - lastPoolExceeded });
        lastPoolExceeded = snap.poolExceeded;
        if (snap.poolCurrentSize > poolHighestCurrent) {
          poolHighestCurrent = snap.poolCurrentSize;
        }

        poolTasksQueued.push({ name: label, value: snap.poolTasksQueued - lastTasksQueued });
        poolTasksFinished.push({ name: label, value: snap.poolTasksFinished - lastTasksFinished });
        poolTasksCancelled.push({ name: label, value: snap.poolTasksCancelled - lastTasksCancelled });

        lastTasksQueued = snap.poolTasksQueued;
        lastTasksFinished = snap.poolTasksFinished;
        lastTasksCancelled = snap.poolTasksCancelled;

        conBytesRead.push({ name: label, value: snap.conBytesRead - lastBytesRead });
        conBytesWritten.push({ name: label, value: snap.conBytesWritten - lastBytesWritten });

        lastBytesRead = snap.conBytesRead;
        lastBytesWritten = snap.conBytesWritten;

        conBytesReadAbs.push({ name: label, value: snap.conBytesRead / (1024 * 1024) });
        conBytesWrittenAbs.push({ name: label, value: snap.conBytesWritten / (1024 * 1024) });
      }

      this.vmCpu.push({ name: 'Threads', series: vmCpuThreadCount });
      this.vmCpuRef.push({ name: 'CPU Count', value: vmCpuCount });

      this.vmMem.push({ name: 'Total Memory MB', series: vmMemTotal });
      this.vmMem.push({ name: 'Used Memory MB', series: vmMemUsed });
      this.vmMemRef.push({ name: 'Max Memory MB', value: vmMemMax / (1024 * 1024) });

      this.req.push({ name: 'Received', series: reqReceived });
      this.req.push({ name: 'Completed', series: reqCompleted });
      this.req.push({ name: 'Cancelled', series: reqCancelled });
      this.req.push({ name: 'Timed Out', series: reqTimedOut });

      this.reqAbs.push({ name: 'Received', series: reqReceivedAbs });
      this.reqAbs.push({ name: 'Completed', series: reqCompletedAbs });
      this.reqAbs.push({ name: 'Cancelled', series: reqCancelledAbs });
      this.reqAbs.push({ name: 'Timed Out', series: reqTimedOutAbs });

      this.poolSize.push({ name: 'Current Size', series: poolCurrentSize });
      this.poolSize.push({ name: 'Times Limit Exceeded', series: poolExceeded });
      this.poolSizeRef.push({ name: 'Core Size', value: poolCoreSize });
      if (poolHighestCurrent * 2 >= poolMaxSize) {
        this.poolSizeRef.push({ name: 'Maximum Pool Size', value: poolMaxSize });
      }

      this.poolTasks.push({ name: 'Queued', series: poolTasksQueued });
      this.poolTasks.push({ name: 'Finished', series: poolTasksFinished });
      this.poolTasks.push({ name: 'Cancelled', series: poolTasksCancelled });

      this.conBytes.push({ name: 'Read', series: conBytesRead });
      this.conBytes.push({ name: 'Written', series: conBytesWritten });

      this.conBytesAbs.push({ name: 'Read', series: conBytesReadAbs });
      this.conBytesAbs.push({ name: 'Written', series: conBytesWrittenAbs });
    });
  }

  private select() {
    this.serverStats = null;
    this.selectedTimer = null;
    this.selectedTimerName = null;
    this.timerSeries = null;
    this.referenceLines = null;

    this.selectedGroup = MetricGroup[this.selection];

    const x: SeriesElement[] = [];

    for (const t of this.getTimers(this.selectedGroup)) {
      const tm = this.getTimer(this.selectedGroup, t);

      x.push({
        name: t,
        value: tm.counter.value,
      });
    }

    this.countGraphHeight = x.length * 25 + 100;
    this.groupCounts = x;
  }

  selectTimer(t: SeriesElement) {
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
  }

  toMillis(nanos: number): number {
    // nanos to millis and round to 2 decimal places
    return Math.round(nanos / 10000) / 100;
  }
}
