import { Route } from '@angular/router';

/**
 * Set unique route id
 */
export function setRouteId(routes: Route[]): Route[] {
  if (!routes) {
    return;
  }

  routes.forEach((route, index) => {
    route.data = {
      ...route.data,
      ...{ routeId: getUniqueId(route.path, index + 1) },
    };
  });
  return routes;
}

/**
 * Generate unique id
 */
function getUniqueId(routeName: string, parts: number): string {
  const stringArr = [];
  stringArr.push(routeName);
  for (let i = 0; i < parts; i++) {
    const S4 = (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);
    stringArr.push(S4);
  }
  return stringArr.join('-');
}
