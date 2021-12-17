import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HistoryEntryType, InstanceNodeConfigurationDto, OperatingSystem, ProcessState } from 'src/app/models/gen.dtos';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { InstancesService } from '../../../../services/instances.service';

/** The percentage in the image per minute on the X axis */
const PERC_PER_MIN = 100 / 14;

/** The percenage amount to position in the graph for a single millisecond. 6.66 periodic percent per minute. */
const PERC_PER_MS = PERC_PER_MIN / 60000;

/** 15 minutes in MS */
const TOTAL_MS = 1000 * 60 * 15;

type MarkedEventType = 'error' | 'warning' | 'info';

interface MarkedEvent {
  description: string;
  type: MarkedEventType;
  time: number;
}

@Component({
  selector: 'app-node-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css'],
})
export class NodeHeaderComponent implements OnInit, OnDestroy {
  @Input() node: InstanceNodeConfigurationDto;
  @Input() show: 'load' | 'cpu';
  @Input() hasAction = true;

  /* template */ events: MarkedEvent[];
  /* template */ curve: number[] = [];
  /* template */ curveLabel: string;
  /* template */ maxValue: number;
  /* template */ maxLabel: string;
  /* template */ renderTime: number = Date.now(); // local time.

  /* template */ pathInfo;
  /* template */ pathPoints;
  /* template */ endMarker = false;
  /* template */ hasVisiblePoint = false;
  /* template */ formatter: (number) => string;

  private subscription: Subscription;

  constructor(private instances: InstancesService, public servers: ServersService) {}

  ngOnInit(): void {
    this.subscription = this.instances.activeNodeStates$.subscribe((states) => {
      if (!states || !states[this.node.nodeName]?.config) {
        this.curve = [];
        this.maxValue = 0;
        this.hasVisiblePoint = false;
        return;
      }

      const state = states[this.node.nodeName].config;

      // first we want to find the curve to plot, either cpu usage (windows), or load average (all others)
      if ((!!this.show && this.show === 'cpu') || (!this.show && state.os === OperatingSystem.WINDOWS)) {
        this.curve = state.monitoring.cpuUsage;
        this.curveLabel = 'System CPU Usage';
        this.maxValue = 1.0; // system cpu load: 0.0 to 1.0
        this.formatter = (n) => Math.round((n * 100 + Number.EPSILON) * 100) / 100 + '%';
      } else {
        this.curve = state.monitoring.loadAvg;
        this.curveLabel = 'Load Average';
        this.maxValue = state.monitoring.availableProcessors;
        this.formatter = (n) => Math.round(((n + Number.EPSILON) * 100) / 100).toString();
      }

      this.maxLabel = 'Available CPUs: ' + state.monitoring.availableProcessors;
      this.renderTime = state.monitoring.timestamp;

      this.update();
    });

    this.subscription.add(
      this.instances.activeHistory$.subscribe((history) => {
        if (!history || !history.events) {
          this.events = [];
          return;
        }

        const limit = this.renderTime - TOTAL_MS; // skip events older than this.
        const eventsInTimeframe = history.events.filter((e) => e.timestamp >= limit);
        const eventsToRender: MarkedEvent[] = [];

        for (const ev of eventsInTimeframe) {
          switch (ev.type) {
            case HistoryEntryType.CREATE:
              eventsToRender.push({ description: `${ev.title} by ${ev.user}`, time: ev.timestamp, type: 'info' });
              break;
            case HistoryEntryType.DEPLOYMENT:
              eventsToRender.push({ description: `${ev.title} by ${ev.user}`, time: ev.timestamp, type: 'info' });
              break;
            case HistoryEntryType.RUNTIME:
              if (ev.runtimeEvent.node !== this.node.nodeName) {
                continue;
              }

              let eventType: MarkedEventType = 'info';
              switch (ev.runtimeEvent.state) {
                case ProcessState.CRASHED_PERMANENTLY:
                  eventType = 'error';
                  break;
                case ProcessState.CRASHED_WAITING:
                case ProcessState.RUNNING_UNSTABLE:
                  eventType = 'warning';
                  break;
              }

              const userInfo = !!ev.user ? ` by ${ev.user}` : '';
              eventsToRender.push({ description: `${ev.title}${userInfo}`, time: ev.timestamp, type: eventType });
              break;
          }
        }

        this.events = eventsToRender;
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private update() {
    this.hasVisiblePoint = false;
    if (!this.curve || !this.maxValue) {
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
        // cubic bezier, with the handles shifted 3% off the start/end points - this will give a nice and smooth curve.
        path += `C ${prevX - 3},${prevY} ${pointX + 3},${pointY} ${pointX},${pointY}`;
      }

      prevX = pointX;
      prevY = pointY;
    }

    this.endMarker = this.pathPoints.length < 15;
    this.pathInfo = path;
  }

  private generatePoints(): { x: number; y: number }[] {
    const result = [];

    // determine the highest value, we want that value to be placed at around 90% of the graph.
    const highestVal = this.getMaxY();
    const data = this.getRelevantDataPoints();

    let curX = 100;
    for (let index = 0; index < data.length; ++index, curX = curX - PERC_PER_MIN) {
      const pointX = curX;
      const pointY = 100 - (data[index] / highestVal) * 100;

      result.push({ x: pointX, y: pointY });
    }

    return result;
  }

  private getMaxY() {
    let highestVal = Math.max(...this.getRelevantDataPoints(), this.maxValue);
    if (highestVal === this.maxValue) {
      // give the label a little room, add 10%
      highestVal = highestVal + highestVal / 10;
    }
    return highestVal;
  }

  private getRelevantDataPoints() {
    if (this.curve.length > 15) {
      return this.curve.slice(0, 15);
    } else {
      return this.curve;
    }
  }

  /* template */ getMaxLineY() {
    return 100 - (this.maxValue / this.getMaxY()) * 100;
  }

  /* template */ getEventX(event: MarkedEvent) {
    const ms = this.renderTime - event.time;
    const perc = ms * PERC_PER_MS;

    if (perc < 100 && perc > 0) {
      return 100 - perc; // from the right side.
    }
    return 0; // outside, don't render
  }
}
