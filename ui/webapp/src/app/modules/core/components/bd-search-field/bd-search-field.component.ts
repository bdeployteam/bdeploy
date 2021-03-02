import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-bd-search-field',
  templateUrl: './bd-search-field.component.html',
  styleUrls: ['./bd-search-field.component.css'],
})
export class BdSearchFieldComponent implements OnInit, OnDestroy {
  @Input() disabled = false;

  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();

  private searchChanged = new Subject<string>();
  private subscription: Subscription;

  constructor() {}

  ngOnInit(): void {
    this.subscription = this.searchChanged.pipe(debounceTime(200)).subscribe((v) => this.valueChange.emit(v));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  queueChange() {
    this.searchChanged.next(this.value);
  }
}
