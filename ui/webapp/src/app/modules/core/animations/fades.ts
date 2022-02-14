import {
  animate,
  state,
  style,
  transition,
  trigger,
} from '@angular/animations';

export const delayedFadeIn = trigger('delayedFadeIn', [
  state(
    'void',
    style({
      opacity: 0,
    })
  ),
  transition('void => *', animate('0.2s {{delay}} ease')),
]);

export const delayedFadeOut = trigger('delayedFadeOut', [
  state(
    'void',
    style({
      opacity: 0,
    })
  ),
  transition('* => void', animate('0.2s {{delay}} ease')),
]);
