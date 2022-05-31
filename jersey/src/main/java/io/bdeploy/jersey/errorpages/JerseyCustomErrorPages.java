package io.bdeploy.jersey.errorpages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;

public class JerseyCustomErrorPages {

    private static final String ERROR_TEMPLATE = readTemplate();
    private static final String LOGO_TEMPLATE = readLogo();

    private static String readTemplate() {
        try (InputStream is = JerseyCustomErrorPages.class.getResourceAsStream("error-template.html")) {
            return new String(StreamHelper.read(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR (no template): {{ERROR}}"; // default, just show *something*, should never happen.
        }
    }

    private static String readLogo() {
        try (InputStream is = JerseyCustomErrorPages.class.getResourceAsStream("logo.svg")) {
            return new String(StreamHelper.read(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<svg></svg>"; // default, should never happen.
        }
    }

    public static String getErrorHtml(String message) {
        return TemplateHelper.process(ERROR_TEMPLATE, v -> {
            switch (v) {
                case "ERROR":
                    return message;
                case "LOGO":
                    return LOGO_TEMPLATE;
                default:
                    return null;
            }

        });
    }

}
