/** Construct URL to navigate to a path on the standalone server. */
export const standalone = (path) => `http://localhost:4210${path}`;

/** Construct URL to navigate to a path on the central server. */
export const central = (path) => `http://localhost:4211${path}`;

/** Construct URL to navigate to a path on the managed server. */
export const managed = (path) => `http://localhost:4212${path}`;
