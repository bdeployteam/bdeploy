import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-search-field',
  templateUrl: './search-field.component.html',
  styleUrls: ['./search-field.component.css'],
})
export class SearchFieldComponent implements OnInit, OnDestroy {
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
