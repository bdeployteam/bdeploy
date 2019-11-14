import { Component, OnInit, ViewChild } from '@angular/core';
import { MatOptionSelectionChange } from '@angular/material';
import { BarHorizontalComponent } from '@swimlane/ngx-charts';
import { MetricBundle, MetricGroup, TimerMetric } from '../models/gen.dtos';
import { MetricsService } from '../services/metrics.service';

export interface SeriesElement {
  name: string;
  value: number;
}

@Component({
  selector: 'app-metrics-overview',
  templateUrl: './metrics-overview.component.html',
  styleUrls: ['./metrics-overview.component.css']
})
export class MetricsOverviewComponent implements OnInit {

  loading = true;
  allMetrics: Map<MetricGroup, MetricBundle>;
  selectedGroup: MetricGroup;
  groupCounts: SeriesElement[];
  selectedTimer: TimerMetric;

  colorScheme = {
    domain: ['#5AA454', '#A10A28', '#C7B42C', '#AAAAAA']
  };
  timerSeries: { name: string, series: SeriesElement[] }[];
  referenceLines: SeriesElement[];

  @ViewChild('countChart', {static: false})
  countChart: BarHorizontalComponent;

  countGraphHeight = 100;

  constructor(private metrics: MetricsService) { }

  ngOnInit() {
    this.metrics.getAllMetrics().subscribe(r => {
      // result is an object with a property per MetricGroup
      this.allMetrics = new Map<MetricGroup, MetricBundle>();
      for (const prop of Object.keys(r)) {
        const group = prop as MetricGroup;
        const item = r[prop] as MetricBundle;

        this.allMetrics.set(group, item);
      }
      this.loading = false;
    });
  }

  getKeys(): MetricGroup[] {
    return Array.from(this.allMetrics.keys());
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

  select(event: MatOptionSelectionChange) {
    if (!event.isUserInput) { return; }

    this.selectedTimer = null;
    this.timerSeries = null;
    this.referenceLines = null;

      this.selectedGroup = event.source.value;

      const x: SeriesElement[] = [];

      for (const t of this.getTimers(this.selectedGroup)) {
        const tm = this.getTimer(this.selectedGroup, t);

        x.push({
          name: t,
          value: tm.counter.value
        });
      }

      this.countGraphHeight = (x.length * 25) + 100;
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
        value: this.toMillis(x)
      });
    }
    this.timerSeries.push({
      name: t.name,
      series: points
    });

    this.referenceLines = [
      { name: 'Mean', value: this.toMillis(timer.histogram.mean) },
      { name: 'Median', value: this.toMillis(timer.histogram.median) },
      { name: '99th percentile', value: this.toMillis(timer.histogram.p99th) },
      { name: '75th percentile', value: this.toMillis(timer.histogram.p75th) },
    ];

    this.selectedTimer = timer;
  }

  toMillis(nanos: number): number {
    // nanos to millis and round to 2 decimal places
    return Math.round((nanos / 10000)) / 100;
  }

}
