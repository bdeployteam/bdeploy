
package io.bdeploy.minion.remote.jersey;

import java.util.ArrayList;
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
        if (mailSenderSettingsDto.password == null) {
            SettingsConfiguration stored = root.getSettings(false);
            if (stored != null && stored.mailSenderSettings != null) {
                mailSenderSettingsDto.password = stored.mailSenderSettings.password;
            }
        }

        URLName url = MessagingUtils.checkAndParseUrl(mailSenderSettingsDto.url, mailSenderSettingsDto.username,
                mailSenderSettingsDto.password);

        InternetAddress senderAddress = StringHelper.isNullOrBlank(mailSenderSettingsDto.senderAddress) ? null
                : MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.senderAddress);

        List<InternetAddress> receiverAddresses = new ArrayList<>();
        if (!StringHelper.isNullOrBlank(mailSenderSettingsDto.receiverAddress)) {
            receiverAddresses.add(MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.receiverAddress));
        }

        try (SMTPTransportConnectionHandler testMailSender = new SMTPTransportConnectionHandler()) {
            testMailSender.connect(url).get();

            MessageDataHolder dataHolder = new MessageDataHolder(senderAddress, receiverAddresses, "Mail sending test",
                    "This is a test mail.", MediaType.TEXT_PLAIN);

            testMailSender.send(dataHolder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | MessagingException | RuntimeException e) {
            throw new IllegalStateException("Mail sending failed.", e);
        }
        return true;
    }

    @Override
    public boolean testSenderConnection(MailSenderSettingsDto mailSenderSettingsDto) {
        if (mailSenderSettingsDto.password == null) {
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
        if (mailReceiverSettingsDto.password == null) {
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
            throw new IllegalStateException("Connection is disabled.");
        }

        if (log.isTraceEnabled()) {
            log.trace("Connection test -> URL={} | username={}", url, username);
        }

        URLName parsedUrl = MessagingUtils.checkAndParseUrl(url, username, password);

        try (ConnectionHandler handler = handlerCreator.get()) {
            handler.connect(parsedUrl).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) {
            throw new IllegalStateException("Connecting failed.", e);
        }
    }
}
