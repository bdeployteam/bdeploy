package io.bdeploy.launcher.cli.branding;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

final class SplashGraphics extends PanelDoubleBuffered implements LauncherSplashDisplay {

    private static final Logger log = LoggerFactory.getLogger(SplashGraphics.class);
    private static final long serialVersionUID = 1L;

    private static Map<RenderingHints.Key, Object> hintsMap = null;
    private final AtomicBoolean fcWarn = new AtomicBoolean(false);

    private transient BufferedImage image;
    private Rectangle text;
    private Rectangle progress;

    private String statusText;
    private int progressMax = 0;
    private int progressCurrent = 0;
    private Color progressColor = Color.DARK_GRAY;
    private Color textColor = Color.BLACK;

    public void setText(Rectangle text) {
        this.text = text;
        repaintTextAndProgress();
    }

    public void setProgress(Rectangle progress) {
        this.progress = progress;
        repaintTextAndProgress();
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    public void setProgressColor(Color progressColor) {
        this.progressColor = progressColor;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        setSize(image.getWidth(), image.getHeight());
        repaint();
    }

    public void repaintTextAndProgress() {
        if (text != null) {
            this.repaint(text.x, text.y, text.width, text.height);
        }
        if (progress != null) {
            this.repaint(progress.x, progress.y, progress.width, progress.height);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Preferred size is determined when the frame is set to visible
        // Not setting this to the size that we set result in a size of 10x10
        return getSize();
    }

    @Override
    public void setStatusText(String statusText) {
        this.statusText = statusText;
        repaintTextAndProgress();
    }

    @Override
    public void setProgressCurrent(int progressCurrent) {
        this.progressCurrent = progressCurrent;
        repaintTextAndProgress();
    }

    @Override
    public void setProgressMax(int progressMax) {
        this.progressMax = progressMax;
        repaintTextAndProgress();
    }

    @Override
    public void paintBuffer(Graphics g) {
        // Using hints on Windows leads to bold text which is hard to read
        Graphics2D g2d = (Graphics2D) g;
        if (OsHelper.getRunningOs() == OperatingSystem.LINUX) {
            g2d.addRenderingHints(getHints());
        }

        // Draw image if available
        g.setClip(null);
        if (image != null) {
            g.drawImage(image, 0, 0, this);
        }

        // Draw border around splash
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, getSize().width - 1, getSize().height - 1);

        // Draw the text
        if (text != null) {
            try {
                g.setColor(textColor);
                g.setClip(text.x, text.y, text.width, text.height);
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(statusText, text.x, text.y + metrics.getHeight());
            } catch (Throwable e) {
                // on some systems drawing text does not work due to fontconfig issues.
                // for now there is nothing we can do except catching the exception and logging it /once/.
                if (fcWarn.compareAndSet(false, true)) {
                    log.warn("There seems to be a problem with font configuration on this machine...", e);
                }
            }
        }

        // Draw the progress bar
        if (progressMax >= 0 && progressCurrent >= 0 && progress != null) {
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
        g.setClip(null);
    }

    private static Map<RenderingHints.Key, Object> getHints() {
        if (hintsMap == null) {
            hintsMap = new HashMap<>();
            hintsMap.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            hintsMap.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hintsMap.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        return hintsMap;
    }
}