
package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.interfaces.settings.CustomAttributeDescriptor;
import io.bdeploy.interfaces.settings.MailReceiverSettingsDto;
import io.bdeploy.interfaces.settings.MailSenderSettingsDto;
import io.bdeploy.interfaces.settings.WebAuthSettingsDto;
import io.bdeploy.messaging.ConnectionHandler;
import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.store.imap.IMAPStoreConnectionHandler;
import io.bdeploy.messaging.transport.smtp.SMTPTransportConnectionHandler;
import io.bdeploy.messaging.util.MessagingUtils;
import io.bdeploy.ui.api.Minion;
import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;
import jakarta.ws.rs.core.MediaType;

public class MasterSettingsResourceImpl implements MasterSettingsResource {

    private static final Logger log = LoggerFactory.getLogger(MasterSettingsResourceImpl.class);

    @Inject
    private Minion root;

    @Override
    public SettingsConfiguration getSettings() {
        return root.getSettings();
    }

    @Override
    public WebAuthSettingsDto getAuthSettings() {
        SettingsConfiguration settings = root.getSettings();

        WebAuthSettingsDto dto = new WebAuthSettingsDto();

        dto.auth0 = settings.auth.auth0Settings;
        dto.okta = settings.auth.oktaSettings;

        return dto;
    }

    @Override
    public void setSettings(SettingsConfiguration settings) {
        root.setSettings(settings);
    }

    @Override
    public void mergeInstanceGroupAttributesDescriptors(List<CustomAttributeDescriptor> attributes) {

        SettingsConfiguration settings = root.getSettings();

        boolean changed = false;
        Map<String, CustomAttributeDescriptor> pMap = settings.instanceGroup.attributes.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p));
        for (CustomAttributeDescriptor a : attributes) {
            CustomAttributeDescriptor existing = pMap.get(a.name);
            if (!a.equals(existing)) { // != or null
                pMap.put(a.name, a);
                changed = true;
            }
        }
        if (changed) {
            settings.instanceGroup.attributes = pMap.values().stream().sorted((a, b) -> a.name.compareTo(b.name)).toList();
            root.setSettings(settings);
        }
    }

    @Override
    public boolean sendTestMail(MailSenderSettingsDto mailSenderSettingsDto) {
        if (StringHelper.isNullOrBlank(mailSenderSettingsDto.password)) {
            SettingsConfiguration stored = root.getSettings(false);
            if (stored != null && stored.mailSenderSettings != null) {
                mailSenderSettingsDto.password = stored.mailSenderSettings.password;
            }
        }

        URLName url = MessagingUtils.checkAndParseUrl(mailSenderSettingsDto.url, mailSenderSettingsDto.username,
                mailSenderSettingsDto.password);

        try (SMTPTransportConnectionHandler testMailSender = new SMTPTransportConnectionHandler()) {
            try {
                testMailSender.connect(url).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException("Connecting was aborted", e);
            }

            InternetAddress senderAddress = StringHelper.isNullOrBlank(mailSenderSettingsDto.senderAddress) ? null
                    : MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.senderAddress);
            InternetAddress receiverAddress = MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.receiverAddress);

            MessageDataHolder dataHolder = new MessageDataHolder(senderAddress, List.of(receiverAddress), "Mail sending test",
                    "This is a test mail.", MediaType.TEXT_PLAIN);

            try {
                testMailSender.send(dataHolder);
            } catch (MessagingException e) {
                throw new IllegalStateException("Failed to send test mail", e);
            }
        }
        return true;
    }

    @Override
    public boolean testSenderConnection(MailSenderSettingsDto mailSenderSettingsDto) {
        if (StringHelper.isNullOrBlank(mailSenderSettingsDto.password)) {
            SettingsConfiguration stored = root.getSettings(false);
            if (stored != null && stored.mailSenderSettings != null) {
                mailSenderSettingsDto.password = stored.mailSenderSettings.password;
            }
        }
        testConnection(mailSenderSettingsDto.enabled, mailSenderSettingsDto.url, mailSenderSettingsDto.username,
                mailSenderSettingsDto.password, SMTPTransportConnectionHandler::new);
        return true;
    }

    @Override
    public boolean testReceiverConnection(MailReceiverSettingsDto mailReceiverSettingsDto) {
        if (StringHelper.isNullOrBlank(mailReceiverSettingsDto.password)) {
            SettingsConfiguration stored = root.getSettings(false);
            if (stored != null && stored.mailReceiverSettings != null) {
                mailReceiverSettingsDto.password = stored.mailReceiverSettings.password;
            }
        }
        testConnection(mailReceiverSettingsDto.enabled, mailReceiverSettingsDto.url, mailReceiverSettingsDto.username,
                mailReceiverSettingsDto.password, IMAPStoreConnectionHandler::new);
        return true;
    }

    private static void testConnection(boolean enabled, String url, String username, String password,
            Supplier<ConnectionHandler> handlerCreator) {
        if (!enabled) {
            throw new IllegalStateException("Mail sending is disabled.");
        }

        if (log.isTraceEnabled()) {
            log.trace("Connection test -> URL=" + url + " | username=" + username);
        }

        URLName parsedUrl = MessagingUtils.checkAndParseUrl(url, username, password);

        try (ConnectionHandler handler = handlerCreator.get()) {
            handler.connect(parsedUrl).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Connecting was aborted", e);
        }
    }
}
