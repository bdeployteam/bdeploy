using System;
using System.Diagnostics;
using System.IO;

namespace Bdeploy
{
    class Program
    {
        static int Main(string[] args)
        {
            // The application to launch must be passed
            if(args.Length != 1)
            {
                Console.WriteLine("ERROR: The descriptor of the application to launch is missing.");
                Console.WriteLine("Usage: Bdeploy.exe myApp.bdeploy");
                Console.WriteLine("Exiting application.");
                return -1;
            }

            // The application descriptor must exist
            string application = args[0];
            if(!File.Exists(application))
            {
                Console.WriteLine("ERROR: Application descriptor '{0}' not found.", application);
                Console.WriteLine("Exiting application.");
                return -1;
            }

            // Start application and wait for exit
            Launcher launcher = new Launcher(application);
            if(!launcher.HasCompanion())
            {
                Console.WriteLine("ERROR: Companion script '{0}' not found.", launcher.GetCompanion());
                Console.WriteLine("Exiting application.");
                return -1;
            }
            return launcher.Start();
        }
    }
}
