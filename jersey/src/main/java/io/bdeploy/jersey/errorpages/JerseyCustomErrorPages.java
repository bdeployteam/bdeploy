package io.bdeploy.jersey.errorpages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;

public class JerseyCustomErrorPages {

    private static final String ERROR_TEMPLATE = readTemplate();
    private static final String LOGO_TEMPLATE = readLogo();
    private static final Map<Integer, String> codeImages = new TreeMap<>();

    private JerseyCustomErrorPages() {
    }

    private static String readTemplate() {
        try (InputStream is = JerseyCustomErrorPages.class.getResourceAsStream("error-template.html")) {
            return new String(StreamHelper.read(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR (no template): {{ERROR}}"; // default, just show *something*, should never happen.
        }
    }

    private static String readErrorImage(int code) {
        try (InputStream is = JerseyCustomErrorPages.class.getResourceAsStream("errors/" + code + ".svg")) {
            return new String(StreamHelper.read(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static synchronized String readErrorImageCached(int code) {
        return codeImages.computeIfAbsent(code, JerseyCustomErrorPages::readErrorImage);
    }

    private static String readLogo() {
        try (InputStream is = JerseyCustomErrorPages.class.getResourceAsStream("logo.svg")) {
            return new String(StreamHelper.read(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<svg></svg>"; // default, should never happen.
        }
    }

    public static String getErrorHtml(int code, String message) {
        return TemplateHelper.process(ERROR_TEMPLATE, v -> {
            switch (v) {
                case "ERROR":
                    return message;
                case "LOGO":
                    String img = readErrorImageCached(code);
                    if (img != null) {
                        return img;
                    }
                    return TemplateHelper.process(LOGO_TEMPLATE, vv -> {
                        if (vv.equals("CODE")) {
                            return String.valueOf(code);
                        } else {
                            return null;
                        }
                    });
                default:
                    return null;
            }

        });
    }

}
