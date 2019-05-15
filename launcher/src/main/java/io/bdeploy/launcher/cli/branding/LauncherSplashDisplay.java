package io.bdeploy.launcher.cli.branding;

public interface LauncherSplashDisplay {

    public void setStatusText(String text);

    public void setProgressCurrent(int current);

    public void setProgressMax(int max);

    public void repaint();

}
