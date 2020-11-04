/**
 * Event object that contains a callback to notify the sender when the event is done.
 */
export class EventWithCallback<T> {
  /**
   * The actual payload to send to the consumer.
   */
  data: T;

  /**
   * Callback to notify when sender when the event is done.
   */
  done: () => void;

  /**
   * Creates a new event object with the given data and callback
   */
  constructor(data: T, done: () => void) {
    this.data = data;
    this.done = done;
  }
}
