/**
 * Enumeration of the state changes send out while installing an update
 */
export enum UpdateStatus {
  UPDATES_AVAILABLE,
  TRANSFER,
  TRANSFER_DONE,
  INSTALL,
  RESTART,
  SUCCESS,
  FAILED
}

export function areUpdatesAvailable(status: UpdateStatus) {
  switch (status) {
    case UpdateStatus.UPDATES_AVAILABLE:
    case UpdateStatus.TRANSFER_DONE:
      return true;
  }
  return false;
}

export function isUpdateInProgress(status: UpdateStatus) {
  switch (status) {
    case UpdateStatus.TRANSFER:
    case UpdateStatus.INSTALL:
    case UpdateStatus.RESTART:
      return true;
  }
  return false;
}

export function isTransferDone(status: UpdateStatus) {
  return status === UpdateStatus.TRANSFER_DONE;
}

export function isUpdateSuccess(status: UpdateStatus) {
  return status === UpdateStatus.SUCCESS;
}

export function isUpdateFailed(status: UpdateStatus) {
  return status === UpdateStatus.FAILED;
}
