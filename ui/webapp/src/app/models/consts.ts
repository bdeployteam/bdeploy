/** HTTP header constant used to suppress global error handling */
export const NO_ERROR_HANDLING_HDR = 'X-No-Global-Error-Handling';

/** "Special" name of node containing client applications. */
export const CLIENT_NODE_NAME = '__ClientApplications';

/** Sort callback for node names, putting 'master' in the first place */
export const sortNodesMasterFirst = (a, b) => {
  if (a === 'master') {
    return -1;
  } else if (b === 'master') {
    return 1;
  } else {
    return a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase());
  }
};
