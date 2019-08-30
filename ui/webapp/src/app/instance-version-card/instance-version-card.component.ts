import { Component, EventEmitter, Input, OnChanges, Output, Predicate } from '@angular/core';
import { InstanceStateRecord, InstanceVersionDto, ManifestKey } from '../models/gen.dtos';

@Component({
  selector: 'app-instance-version-card',
  templateUrl: './instance-version-card.component.html',
  styleUrls: ['./instance-version-card.component.css'],
})
export class InstanceVersionCardComponent implements OnChanges {
  @Input() instanceVersionDto: InstanceVersionDto;
  @Input() selected: boolean;
  @Input() dirty: boolean;
  @Input() disabled: boolean;
  @Input() productAvailable: boolean;
  @Input() state: InstanceStateRecord;
  @Input() isRunningOrScheduled: boolean;
  @Input() instanceGroup: string;
  @Input() instanceUuid: string;

  @Output() install = new EventEmitter<ManifestKey>();
  @Output() activate = new EventEmitter<ManifestKey>();
  @Output() uninstall = new EventEmitter<ManifestKey>();
  @Output() export = new EventEmitter<ManifestKey>();

  isLoading: boolean;
  isActive: boolean;
  isDeployed: boolean;
  isRunning: boolean;

  constructor() {}

  ngOnChanges() {
    if (!this.state || this.dirty) {
      this.isActive = false;
      this.isDeployed = false;
    } else {
      this.isActive = this.state.activeTag === this.instanceVersionDto.key.tag;
      this.isDeployed =
        this.state.installedTags.findIndex(this.tagPredicate()) !== -1 &&
        this.state.activeTag !== this.instanceVersionDto.key.tag;
    }
  }

  getCardStyle(): string[] {
    const styles: string[] = [];
    if (this.dirty) {
      styles.push('instance-version-modified');
    }
    if (this.disabled || !this.productAvailable) {
      styles.push('instance-version-disabled');
    }
    if (this.selected) {
      styles.push('instance-version-selected');
    }
    return styles;
  }

  tagPredicate(): Predicate<string> {
    return x => x === this.instanceVersionDto.key.tag;
  }
}
