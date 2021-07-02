using System;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Enumeration defining whether to retry or cancel an operation
    /// </summary>
    public enum RetryCancelMode {
        RETRY, CANCEL
    }

    /// <summary>
    /// Event data that allows cancellation or retry of an operation.
    /// </summary>
    public class RetryCancelEventArgs : EventArgs {
        /// <summary>
        /// Result object to be filled by the consumer of the event.
        /// </summary>
        public RetryCancelMode Result { get; set; }

    }

    /// <summary>
    /// Event data that allows cancellation of an operation.
    /// </summary>
    public class CancelEventArgs : EventArgs {
        /// <summary>
        /// Result object to be filled by the consumer of the event.
        /// </summary>
        public bool Result { get; set; }

    }

    /// <summary>
    /// The message to display.
    /// </summary>
    public class MessageEventArgs : EventArgs {
        public string Message { get; private set; }
        public MessageEventArgs(string message) {
            this.Message = message;
        }
    }


}
