import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailTask implements Runnable{
    private Message message;

    MailTask(MailMessage msg, Session session, Credentials credentials) {
        try {
            Address[] reciepents = msg.getRecipients();
            message = new MimeMessage(session);
            String type = (msg.isHTML() ? "text/html" : "text/plain") + "; charset=utf-8";
            message.setFrom(new InternetAddress(credentials.getUsername()));
            message.setRecipients(Message.RecipientType.TO, reciepents);
            message.setSubject(msg.getSubject());
            message.setContent(msg.getContent(), type);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
    
}
