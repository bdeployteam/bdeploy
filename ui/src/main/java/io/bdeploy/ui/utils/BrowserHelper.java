package io.bdeploy.ui.utils;

import com.vaadin.open.Open;

import io.bdeploy.common.cfg.RemoteValidator;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.AuthResource;

/**
 * Contains utility methods for opening an URL in a browser.
 */
public class BrowserHelper {

    public static final String API_SUFFIX = "/api";

    private BrowserHelper() {
    }

    /**
     * Opens the BDeploy web UI in the default browser.
     *
     * @param service The remote service to open the web UI of
     * @param urlExtension An optional addition to the URL
     * @return <code>true</code> if the url was launched, otherwise <code>false</code>
     */
    public static boolean openUrl(RemoteService service, String urlExtension) {
        String url = service.getUri().toString();
        if (url.endsWith(RemoteValidator.API_SUFFIX)) {
            url = url.substring(0, url.length() - RemoteValidator.API_SUFFIX.length());
        }
        if (!StringHelper.isNullOrBlank(urlExtension)) {
            url += urlExtension;
        }
        return Open.open(url + "?otp=" + ResourceProvider.getResource(service, AuthResource.class, null).createSessionWithOtp());
    }
}
