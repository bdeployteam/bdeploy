import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { format } from 'date-fns';
import { Subscription } from 'rxjs';
import { ClientsUsageService, ClientUsagePerApp } from '../../../../services/clients-usage.service';

const PERC_PER_DAY = 100 / 29;

@Component({
  selector: 'app-client-usage-graph',
  templateUrl: './usage-graph.component.html',
  styleUrls: ['./usage-graph.component.css'],
})
export class ClientUsageGraphComponent implements OnInit, OnDestroy, OnChanges {
  @Input() appUid: string;

  private subscription: Subscription;
  private usage: ClientUsagePerApp[];

  /* template */ curve: number[] = [];
  /* template */ dayLabels: string[] = [];
  /* template */ pathPoints: { x: number; y: number }[];
  /* template */ pathInfo: string;
  /* template */ hasVisiblePoint = false;
  /* template */ endMarker = false;

  constructor(private clients: ClientsUsageService) {}

  ngOnInit(): void {
    this.subscription = this.clients.clientUsage$.subscribe((usage) => {
      this.usage = usage;
      this.update();
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  ngOnChanges() {
    this.update();
  }

  private update() {
    const usageOfApp = this.usage?.find((u) => u.appUid === this.appUid);

    if (!usageOfApp) {
      this.curve = null;
      this.pathPoints = null;
      this.hasVisiblePoint = false;
    } else {
      const reverseDailyUsage = [...usageOfApp.usage].reverse();
      this.curve = reverseDailyUsage.map((u) => u.usage.reduce((p, c) => p + c.usage, 0));
      this.dayLabels = reverseDailyUsage.map((u) => format(u.day, 'dd.MM.yyyy'));
    }

    if (!this.curve) {
      // no data to render in the main graph.
      return;
    }

    this.generatePath();
  }

  private generatePath() {
    let path = '';

    const highestVal = this.getMaxY();
    this.pathPoints = this.generatePoints();

    let prevX = 100;
    let prevY = 100;
    for (let index = 0; index < this.pathPoints.length; ++index) {
      const pointX = this.pathPoints[index].x;
      const pointY = this.pathPoints[index].y;

      if (pointX >= 0 && pointX <= 100 && pointY >= 0 && pointY <= 100) {
        this.hasVisiblePoint = true;
      }

      if (index === 0) {
        // just move there it is the corner of the graph.
        path += `M ${pointX},${pointY}`;
      } else {
        // cubic bezier, with the handles shifted 2% off the start/end points - this will give a nice and smooth curve.
        path += `C ${prevX - 2},${prevY} ${pointX + 2},${pointY} ${pointX},${pointY}`;
      }

      prevX = pointX;
      prevY = pointY;
    }

    this.endMarker = this.pathPoints.length < 30;
    this.pathInfo = path;
  }

  private generatePoints(): { x: number; y: number }[] {
    const result = [];

    // determine the highest value, we want that value to be placed at around 90% of the graph.
    const highestVal = this.getMaxY();
    const data = this.getRelevantDataPoints();

    let curX = 100;
    for (let index = 0; index < data.length; ++index, curX = curX - PERC_PER_DAY) {
      const pointX = curX;
      const pointY = 100 - (data[index] / highestVal) * 100;

      result.push({ x: pointX, y: pointY });
    }

    return result;
  }

  private getMaxY() {
    let highestVal = Math.max(...this.getRelevantDataPoints());

    // give the graph a little room, add 10%
    highestVal = highestVal + highestVal / 10;

    return highestVal;
  }

  private getRelevantDataPoints() {
    if (this.curve.length > 30) {
      return this.curve.slice(0, 30);
    } else {
      return this.curve;
    }
  }
}
