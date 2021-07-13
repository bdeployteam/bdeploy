import { Component, OnInit } from '@angular/core';
import { ActivitiesService, ActivitySnapshotTreeNode } from '../../services/activities.service';

interface SquashedActivity {
  current: ActivitySnapshotTreeNode;
  parent: ActivitySnapshotTreeNode;
  parentInfo: string[];
  header: string;
}

@Component({
  selector: 'app-bd-activities',
  templateUrl: './bd-activities.component.html',
  styleUrls: ['./bd-activities.component.css'],
})
export class BdActivitiesComponent implements OnInit {
  constructor(public activities: ActivitiesService) {}

  ngOnInit(): void {}

  /* template */ squashActivity(act: ActivitySnapshotTreeNode): SquashedActivity {
    const result = { current: act, parent: act, parentInfo: [], header: null };

    result.current = this.doSquash(act, result);
    result.header = !result.parentInfo?.length ? result.current?.snapshot?.name : result.parentInfo.join(' / ');

    return result;
  }

  /* template */ onDismiss(squashed: SquashedActivity) {
    this.activities.cancelActivity(squashed.current.snapshot.uuid).subscribe();
  }

  /* template */ doTrack(index: number, item: ActivitySnapshotTreeNode) {
    return index;
  }

  /* template */ calculatePercentDone(squashed: SquashedActivity) {
    return Math.round((100 * squashed.current.snapshot.current) / squashed.current.snapshot.max);
  }

  /* template */ formatDuration(node: ActivitySnapshotTreeNode) {
    const ms = node?.snapshot?.duration;
    const sec = Math.floor(ms / 1000) % 60;
    const min = Math.floor(ms / 60000) % 60;
    const hours = Math.floor(ms / 3600000) % 24;
    const days = Math.floor(ms / 86400000);

    let s = '';
    if (days > 0) {
      s = s + days + (days === 1 ? ' day ' : ' days ');
    }
    if (hours > 0 || days > 0) {
      s = s + hours + (hours === 1 ? ' hour ' : ' hours ');
    }
    if (min > 0 || hours > 0 || days > 0) {
      s = s + min + (min === 1 ? ' minute' : ' minutes');
    }
    if (days === 0 && hours === 0 && min === 0) {
      s = s + sec + (sec === 1 ? ' second' : ' seconds');
    }
    return s;
  }

  private doSquash(act: ActivitySnapshotTreeNode, target: SquashedActivity): ActivitySnapshotTreeNode {
    target.parentInfo.push(act.snapshot.name);

    if (act?.children?.length) {
      return this.doSquash(act.children[act.children.length - 1], target);
    }

    return act;
  }
}
