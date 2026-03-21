import java.util.Properties;
import javax.mail.Session;

public class MailSession {
    private Credentials credentials;
    private Properties properties;
    private Session session;

    public MailSession(Credentials credentials) {
        this.credentials = credentials;
        properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        if (!credentials.isValid()) return;
        session = Session.getInstance(properties, (new javax.mail.Authenticator() {
            @Override
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(credentials.getUsername(), credentials.getPassword());
            }
        }));
    }

    public MailTask getTask(MailMessage msg) {
        if (!credentials.isValid()) return null;
         try {
            return new MailTask(msg, session, credentials);
        } catch (Exception e) {
            System.err.println("Error sending email. Check your credentials and connection.");
            e.printStackTrace();
        }
        return null;
    }

}
