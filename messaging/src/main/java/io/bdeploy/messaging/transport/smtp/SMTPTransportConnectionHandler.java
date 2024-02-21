package io.bdeploy.messaging.transport.smtp;

import java.util.Properties;

import org.eclipse.angus.mail.smtp.SMTPMessage;
import org.eclipse.angus.mail.smtp.SMTPSSLTransport;
import org.eclipse.angus.mail.smtp.SMTPTransport;

import io.bdeploy.messaging.transport.TransportConnectionHandler;
import jakarta.mail.Message;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;

/**
 * Sends {@link Message messages} from one single {@link InternetAddress sender address} to an array of recipients using SMTP or
 * SMTPS.
 */
public class SMTPTransportConnectionHandler extends TransportConnectionHandler<SMTPTransport> {

    @Override
    protected void modifyProperties(Properties properties) {
        super.modifyProperties(properties);
        properties.put("mail." + getProtocol() + ".sendpartial", "true");
    }

    @Override
    protected Session createSession(Properties properties) throws NoSuchProviderException {
        String protocol = getProtocol();
        switch (protocol) {
            case "smtp":
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.auth", "true");
                return Session.getInstance(properties);
            case "smtps":
                return Session.getInstance(properties);
        }
        throw getNoSuchProviderException(protocol);
    }

    @Override
    protected SMTPTransport createService(URLName url) throws NoSuchProviderException {
        String protocol = getProtocol();
        switch (protocol) {
            case "smtp":
                return new SMTPTransport(getSession(), url);
            case "smtps":
                return new SMTPSSLTransport(getSession(), url);
        }
        throw getNoSuchProviderException(protocol);
    }

    @Override
    protected Message createMessage() {
        return new SMTPMessage(getSession());
    }
}
