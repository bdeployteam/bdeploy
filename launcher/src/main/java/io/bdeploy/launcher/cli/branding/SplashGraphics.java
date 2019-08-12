package io.bdeploy.launcher.cli.branding;

import java.awt.Color;
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

final class SplashGraphics extends PanelDoubleBuffered implements LauncherSplashDisplay {

    private static final Logger log = LoggerFactory.getLogger(SplashGraphics.class);

    private static final long serialVersionUID = 1L;
    private final transient BufferedImage image;
    private final Rectangle text;
    private final Rectangle progress;
    private final AtomicBoolean fcWarn = new AtomicBoolean(false);

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
        ((Graphics2D) g).addRenderingHints(getHints());
        g.setClip(null);
        g.drawImage(image, 0, 0, this);

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
        g.setClip(null);
    }

    private static Map<RenderingHints.Key, Object> hintsMap = null;

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