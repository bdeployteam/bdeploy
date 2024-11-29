import { ChangeDetectorRef, Directive, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { NgControl } from '@angular/forms';
import { Observable, Subscription } from 'rxjs';

@Directive({
    selector: '[appRevalidateOn]',
    standalone: false
})
export class RevalidateOnDirective implements OnInit, OnDestroy {
  @Input() appRevalidateOn: Observable<unknown>;
  private readonly host = inject(NgControl);
  private readonly cd = inject(ChangeDetectorRef);

  private sub: Subscription;

  ngOnInit(): void {
    if (!this.appRevalidateOn) {
      return; // error...
    }

    this.sub = this.appRevalidateOn.subscribe(() => {
      // need to do this outside the update loop.
      setTimeout(() => {
        this.host.control.updateValueAndValidity();
        this.cd.detectChanges();
      }, 50);
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
