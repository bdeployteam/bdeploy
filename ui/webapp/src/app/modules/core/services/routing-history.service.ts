import { Location } from '@angular/common';
import { Injectable } from '@angular/core';
import { NavigationStart, Router, RoutesRecognized } from '@angular/router';
import { filter, pairwise } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class RoutingHistoryService {
  private offset = 0;
  private routedBack = false;

  constructor(private router:Router, private location:Location) {
    router.events.pipe(filter(e => e instanceof NavigationStart)).subscribe((e:NavigationStart) => {
      if(e.navigationTrigger === "popstate"){
        this.routedBack = true;
        this.offset--;
      }
    });
    router.events.pipe(filter(e => e instanceof RoutesRecognized),pairwise()).subscribe(() => {
      if(!this.routedBack){
        this.offset++;
      }
      this.routedBack = false;
    });
  }

  back(defaultUrl:string){
    if(this.offset>0){
      this.location.back();
    }
    else{
      this.routedBack = true;
      this.router.navigateByUrl(defaultUrl);
    }
  }
}
