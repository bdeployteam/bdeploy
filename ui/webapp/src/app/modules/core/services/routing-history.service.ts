import { Injectable } from '@angular/core';
import { Router, RoutesRecognized } from '@angular/router';
import { filter, pairwise } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class RoutingHistoryService {
  previousUrls:string[] = [];
  i:RoutesRecognized;
  constructor(private router:Router) {
    router.events.pipe(filter(e => e instanceof RoutesRecognized),pairwise()).subscribe((e:any) => this.previousUrls.push(e.urlAfterRedirects));
  }
  back(defaultUrl:string){
    if(this.previousUrls.length > 0){
      this.router.navigateByUrl(this.previousUrls.pop())
    }
    else{
      this.router.navigateByUrl(defaultUrl);
    }
  }
}
