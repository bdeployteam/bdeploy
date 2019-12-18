package io.bdeploy.launcher.cli.branding;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationBrandingDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationSplashAreaDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationSplashDescriptor;
import io.bdeploy.launcher.cli.ui.WindowHelper;

/**
 * Represents the splash screen which paints an image along with a progress text and progress bar.
 */
public class LauncherSplash implements LauncherSplashDisplay {

    private static final Logger log = LoggerFactory.getLogger(LauncherSplash.class);

    private final Path appDir;
    private Frame splash;
    private SplashGraphics splashComp;

    public LauncherSplash(Path appDir) {
        this.appDir = appDir;
    }

    public void show() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        splash = new Frame("Splash");
        splash.setUndecorated(true);
        splash.setSize(480, 280);
        splash.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

        splashComp = new SplashGraphics();
        splashComp.setSize(480, 280);
        splashComp.setStatusText("Loading");
        splash.add(splashComp);

        // try to load previously stored splash or use default
        BufferedImage splashImage = loadSplashImage();
        if (splashImage != null) {
            splashComp.setImage(splashImage);
        } else {
            splashImage = loadResource("/splash.bmp");
            if (splashImage != null) {
                splashComp.setImage(splashImage);
            }
            splashComp.setText(new Rectangle(15, 220, 450, 20));
            splashComp.setProgress(new Rectangle(15, 245, 450, 12));
        }

        // Try to load previously stored icon or use default
        BufferedImage iconImage = loadIconImage();
        if (iconImage != null) {
            splash.setIconImage(iconImage);
        } else {
            iconImage = loadResource("/logo128.png");
            if (iconImage != null) {
                splash.setIconImage(iconImage);
            }
        }

        // Update according to stored data
        Path cached = appDir.resolve("splash.json");
        if (Files.exists(cached)) {
            try {
                ApplicationBrandingDescriptor cache = StorageHelper.fromPath(cached, ApplicationBrandingDescriptor.class);
                updateSplashData(cache.splash);
            } catch (Exception e) {
                log.warn("Cannot load splash from cache: {}", cached, e);
            }
        }

        // Show splash
        WindowHelper.center(splash);
        splash.setVisible(true);
    }

    /** Tries to load an embedded resource */
    private BufferedImage loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return ImageIO.read(in);
        } catch (Exception ex) {
            log.warn("Cannot load embedded resource {}.", path, ex);
            return null;
        }
    }

    /** Tries to load the stored splash screen */
    private BufferedImage loadSplashImage() {
        return findFile("splash");
    }

    /** Tries to load the stored icon image for the frame */
    private BufferedImage loadIconImage() {
        return findFile("icon");
    }

    /** Tries to find an image file with the given prefix */
    private BufferedImage findFile(String prefix) {
        Set<String> supportedTypes = Arrays.stream(ImageIO.getReaderFormatNames()).map(String::toLowerCase)
                .collect(Collectors.toSet());
        File appFile = appDir.toFile();
        File[] files = appFile.listFiles(pathname -> {
            String name = pathname.getName();
            String extension = PathHelper.getExtension(name).toLowerCase();
            return name.startsWith(prefix) && supportedTypes.contains(extension);
        });

        // Take first applicable file
        if (files.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(files[0]);
        } catch (Exception ex) {
            log.warn("Cannot load cached image from file.", ex);
            return null;
        }
    }

    /**
     * Updates the tray icon of the splash screen.
     */
    public void updateIconImage(byte[] imageData) {
        BufferedImage image = load(imageData);
        if (image == null || splash == null) {
            return;
        }
        splash.setIconImage(image);
    }

    /**
     * Writes the icon image to the file-system.
     */
    public void storeIconImage(String name, byte[] imageData) {
        String splashType = PathHelper.getExtension(name);
        Path path = appDir.resolve("icon." + splashType);
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(imageData);
        } catch (IOException e) {
            log.warn("Cannot cache icon image to {}", path, e);
        }
    }

    /**
     * Updates the image of the splash.
     */
    public void updateSplashImage(byte[] imageData) {
        BufferedImage image = load(imageData);
        if (image == null || splash == null) {
            return;
        }
        splashComp.setImage(image);
    }

    /**
     * Writes the splash image to the file-system.
     */
    public void storeSplashImage(String name, byte[] imageData) {
        String splashType = PathHelper.getExtension(name);
        Path path = appDir.resolve("splash." + splashType);
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(imageData);
        } catch (IOException e) {
            log.warn("Cannot cache splash screen to {}", path, e);
        }
    }

    /**
     * Updates the splash screen according to the given descriptor.
     */
    public void updateSplashData(ApplicationSplashDescriptor splashCfg) {
        if (splash == null) {
            return;
        }

        // colors
        if (splashCfg.textRect != null && splashCfg.textRect.foreground != null) {
            Color textColor = Color.decode(splashCfg.textRect.foreground);
            splashComp.setTextColor(textColor);
        }
        if (splashCfg.progressRect != null && splashCfg.progressRect.foreground != null) {
            Color progressColor = Color.decode(splashCfg.progressRect.foreground);
            splashComp.setProgressColor(progressColor);
        }

        // text and progress
        Rectangle textRect = convert(splashCfg.textRect);
        if (textRect != null) {
            splashComp.setText(textRect);
        }
        Rectangle progressRect = convert(splashCfg.progressRect);
        if (progressRect != null) {
            splashComp.setProgress(progressRect);
        }

        // size and position
        splash.setSize(splashComp.getWidth(), splashComp.getHeight());
        WindowHelper.center(splash);
        EventQueue.invokeLater(() -> splash.repaint());
    }

    /**
     * Writes the splash configuration to the file-system
     */
    public void storeSplashData(ApplicationSplashDescriptor splashCfg) {
        Path path = appDir.resolve("splash.json");
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(StorageHelper.toRawBytes(splashCfg));
        } catch (IOException e) {
            log.warn("Cannot cache splash data to {}", path, e);
        }
    }

    private Rectangle convert(ApplicationSplashAreaDescriptor area) {
        if (area == null) {
            return null;
        }
        return new Rectangle(area.x, area.y, area.width, area.height);
    }

    private BufferedImage load(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(bais);
        } catch (Exception e) {
            log.warn("Cannot load image from supplied bytes", e);
            return null;
        }
    }

    public void dismiss() {
        if (splash == null) {
            return;
        }
        splash.setVisible(false);
        splash.dispose();
        splash = null;
    }

    @Override
    public void setStatusText(String text) {
        if (splashComp != null) {
            splashComp.setStatusText(text);
        }
    }

    @Override
    public void setProgressCurrent(int current) {
        if (splashComp != null) {
            splashComp.setProgressCurrent(current);
        }
    }

    @Override
    public void setProgressMax(int max) {
        if (splashComp != null) {
            splashComp.setProgressMax(max);
        }
    }

    @Override
    public void repaint() {
        if (splashComp != null) {
            splashComp.repaintTextAndProgress();
        }
    }

}
