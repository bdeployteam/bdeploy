package io.bdeploy.launcher.cli.branding;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationSplashAreaDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationSplashDescriptor;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.cli.WindowHelper;

public class LauncherSplash implements LauncherSplashDisplay {

    private static final Logger log = LoggerFactory.getLogger(LauncherSplash.class);

    private final ClickAndStartDescriptor cd;
    private final Path imageCache;
    private Window splash;

    private SplashGraphics splashComp;

    public LauncherSplash(ClickAndStartDescriptor cd, Path imageCache) {
        this.cd = cd;
        this.imageCache = imageCache;

        if (!Files.isDirectory(imageCache)) {
            PathHelper.mkdirs(imageCache);
        }
    }

    public void show() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        splash = new Window(null);
        splash.setSize(480, 280);
        splash.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        WindowHelper.center(splash);
        splash.setVisible(true);

        Path cached = getCachePath();
        if (!Files.exists(cached)) {
            initSplash();
        } else {
            try {
                LauncherSplashCacheDto cache = StorageHelper.fromPath(cached, LauncherSplashCacheDto.class);
                update(cache.data, cache.spec);
            } catch (Exception e) {
                log.warn("Cannot load splash from cache: {}", cached, e);
                initSplash();
            }
        }

    }

    private void initSplash() {
        Label init = new Label("Loading...");
        init.setSize(480, 280);
        init.setAlignment(Label.CENTER);
        splash.add(init);
    }

    private Path getCachePath() {
        return imageCache.resolve(cd.groupId).resolve(cd.instanceId).resolve(cd.applicationId + ".json");
    }

    public void update(ClientApplicationConfiguration cfg) {
        ApplicationSplashDescriptor splashCfg = cfg.clientDesc.branding == null ? null : cfg.clientDesc.branding.splash;
        update(cfg.clientSplashData, splashCfg);

        LauncherSplashCacheDto cache = new LauncherSplashCacheDto();
        cache.data = cfg.clientSplashData;
        cache.spec = splashCfg;

        Path cached = getCachePath();
        PathHelper.mkdirs(cached.getParent());

        try (OutputStream os = Files.newOutputStream(cached)) {
            os.write(StorageHelper.toRawBytes(cache));
        } catch (IOException e) {
            log.warn("Cannot cache splash screen to {}", cached, e);
        }
    }

    private void update(byte[] data, ApplicationSplashDescriptor splashCfg) {
        if (data == null) {
            return; // no splash.
        }

        BufferedImage image = load(data);

        if (splashCfg == null || splashCfg.textRect == null) {
            splashCfg = new ApplicationSplashDescriptor();

            if (image != null) {
                splashCfg.textRect = new ApplicationSplashAreaDescriptor(10, image.getHeight() - 60, image.getWidth() - 20, 20,
                        null);
            } else {
                splashCfg.textRect = new ApplicationSplashAreaDescriptor(10, 130, 460, 20, null);
            }
        }

        // colors...
        Color textColor = Color.BLACK;
        Color progressColor = Color.DARK_GRAY;

        if (splashCfg.textRect != null && splashCfg.textRect.foreground != null) {
            textColor = Color.decode(splashCfg.textRect.foreground);
        }

        if (splashCfg.progressRect != null && splashCfg.progressRect.foreground != null) {
            progressColor = Color.decode(splashCfg.progressRect.foreground);
        }

        if (splash == null) {
            return;
        }

        // remove previous component.
        if (splash.getComponentCount() > 0) {
            splash.remove(0);
        }

        // add image splash.
        splashComp = new SplashGraphics(image, convert(splashCfg.textRect), convert(splashCfg.progressRect), textColor,
                progressColor);
        splash.add(splashComp);

        splash.setSize(splashComp.getWidth(), splashComp.getHeight());
        WindowHelper.center(splash);

        EventQueue.invokeLater(() -> splash.repaint());
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
        } catch (IOException e) {
            log.warn("Cannot load splash screen from supplied bytes", e);
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
