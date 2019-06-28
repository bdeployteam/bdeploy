package io.bdeploy.launcher.cli.branding;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

final class SplashGraphics extends PanelDoubleBuffered implements LauncherSplashDisplay {

    private static final long serialVersionUID = 1L;
    private final transient BufferedImage image;
    private final Rectangle text;
    private final Rectangle progress;

    private String statusText = "Initializing...";
    private int progressMax = 0;
    private int progressCurrent = 0;
    private final Color progressColor;
    private final Color textColor;

    public SplashGraphics(BufferedImage image, Rectangle text, Rectangle progress, Color textColor, Color progressColor) {
        this.image = image;
        this.text = text;
        this.progress = progress;
        this.textColor = textColor;
        this.progressColor = progressColor;

        setSize(image.getWidth(), image.getHeight());
    }

    public void repaintTextAndProgress() {
        this.repaint(text.x, text.y, text.width, text.height);
        if (progress != null) {
            this.repaint(progress.x, progress.y, progress.width, progress.height);
        }
    }

    @Override
    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    @Override
    public void setProgressCurrent(int progressCurrent) {
        this.progressCurrent = progressCurrent;
    }

    @Override
    public void setProgressMax(int progressMax) {
        this.progressMax = progressMax;
    }

    @Override
    public void paintBuffer(Graphics g) {
        g.drawImage(image, 0, 0, this);

        g.setColor(textColor);
        g.setClip(text.x, text.y, text.width, text.height);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(statusText, text.x, text.y + metrics.getHeight());

        if (progressMax <= 0 || progressCurrent <= 0 || progress == null) {
            return;
        }

        int fillWidth;
        if (progressCurrent >= progressMax) {
            fillWidth = progress.width;
        } else {
            float factor = (float) progressCurrent / (float) progressMax;
            fillWidth = (int) (progress.width * factor);
        }

        g.setColor(progressColor);
        g.setClip(progress.x, progress.y, progress.width, progress.height);
        g.fillRect(progress.x, progress.y, fillWidth, progress.height);
    }

}