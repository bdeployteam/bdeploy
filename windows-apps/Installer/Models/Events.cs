using System;

namespace Bdeploy.Installer
{
    /// <summary>
    /// Data when icon has been loaded.
    /// </summary>
    public class IconEventArgs : EventArgs
    {
        public string Icon { get; private set; }

        public IconEventArgs(string Icon)
        {
            this.Icon = Icon;
        }
    }

    /// <summary>
    /// Data when an error occurred.
    /// </summary>
    public class MessageEventArgs : EventArgs
    {
        public string Message { get; private set; }

        public MessageEventArgs(string message)
        {
            this.Message = message;
        }
    }

    /// <summary>
    /// Data when some work has been done
    /// </summary>
    public class WorkedEventArgs : EventArgs
    {
        public long Worked { get; private set; }

        public WorkedEventArgs(long worked)
        {
            this.Worked = worked;
        }
    }

    /// <summary>
    /// Data when a new sub task is started
    /// </summary>
    public class SubTaskEventArgs : EventArgs
    {
        public string TaskName { get; private set; }
        public long TotalWork { get; private set; }

        public SubTaskEventArgs(string taskName, long totalWork)
        {
            this.TaskName = taskName;
            this.TotalWork = totalWork;
        }
    }
}
