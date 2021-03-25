import { ElementRef, Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { AuthenticationService } from './authentication.service';

export function bdGuideFor(val: string): { selector: string; template: string } {
  return { selector: `${val}-guide`, template: '' };
}

export interface Guide {
  id: string;
  type: GuideType;
  elements: GuidedElement[];
}

export interface GuidedElement {
  element?: ElementRef<any>;

  header: string;
  content: string;

  beforeElement?: () => boolean;
  afterElement?: () => void;
}

export interface GuidedElementRef {
  guide: Guide;
  element: GuidedElement;
  index: number;
}

export enum GuideType {
  USER = 'USER',
  DEVELOPER = 'DEVELOPER',
  NONE = 'NONE',
}

@Injectable({
  providedIn: 'root',
})
export class GuideService {
  public element$ = new BehaviorSubject<GuidedElementRef>(null);
  private state: GuidedElementRef = null;

  private guides: Guide[] = [];
  public currentType: GuideType;

  constructor(private authService: AuthenticationService) {
    this.loadType();
    this.authService.getTokenSubject().subscribe((t) => {
      if (!!t) {
        this.next();
      }
    });
  }

  private loadType() {
    this.currentType = localStorage.getItem('guideType') as GuideType;
    if (!this.currentType) {
      this.currentType = GuideType.USER;
    }
  }

  public saveType(val: GuideType) {
    this.currentType = val;
    localStorage.setItem('guideType', val);
  }

  private loadVisited(): string[] {
    const stored = localStorage.getItem('guideVisits');
    if (!stored) {
      return [];
    }
    return JSON.parse(stored) as string[];
  }

  private saveVisited(val: string[]) {
    localStorage.setItem('guideVisits', JSON.stringify(val));
  }

  public register(guide: Guide) {
    if (!this.guides.find((g) => g.id === guide.id)) {
      this.guides.push(guide);
    }

    if (!this.state) {
      this.next();
    }
  }

  public next(): void {
    if (this.currentType === GuideType.NONE || !this.authService.isAuthenticated()) {
      if (!!this.state) {
        this.state = null;
        this.element$.next(this.state);
      }
      return; // no guides allowed.
    }

    let guide = this.state?.guide;
    let index = !guide ? 0 : this.state.index;

    // conclude the previous element if it exists.
    if (!!this.state?.element?.afterElement) {
      this.state.element.afterElement();
    }

    if (!guide || guide.elements.length === index + 1) {
      // no active guide or end of previous guide, fetch one from the list
      const visited = this.loadVisited();

      // if we're at the end of a guide, store it as "visited"
      if (!!guide) {
        if (!visited.find((id) => id === guide.id)) {
          visited.push(guide.id);
          this.saveVisited(visited);
        }
      }

      // find the next guide to show
      index = 0;
      do {
        guide = this.guides.shift();
      } while (!!guide && (guide.type !== this.currentType || !!visited.find((p) => p === guide.id)));
    } else if (guide.elements.length > index + 1) {
      index++;
    }

    // continue on to the next element
    if (!!guide) {
      if (!!guide.elements[index].beforeElement) {
        const showStep = guide.elements[index].beforeElement();
        if (!showStep) {
          this.next();
        }
      }
      // we're using a timeout here to avoid issues with animations.
      this.state = { guide, element: guide.elements[index], index: index };
    } else {
      this.state = null;
    }
    setTimeout(() => this.element$.next(this.state), 250);
  }

  public skip(): void {
    const guide = this.element$.value?.guide;
    if (!!guide) {
      this.element$.value.index = guide.elements.length - 1;
      this.next();
    }
  }

  public disable(): void {
    this.saveType(GuideType.NONE);
    this.next();
  }

  public enable(type: GuideType): void {
    this.saveType(type);
    this.saveVisited([]);
  }
}
