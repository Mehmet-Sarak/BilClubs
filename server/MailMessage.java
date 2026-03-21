import java.util.ArrayList;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public class MailMessage {
    private String content;
    private String subject;
    private boolean isHTML = false;
    private ArrayList<Address> recipients = new ArrayList<>();

    public void setContent(String content) {
        this.content = content;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void useHTML() {
        isHTML = true;
    }

    public boolean isHTML() {
        return isHTML;
    }

    public void fromTemplate(HTMLTemplate template) {
        isHTML = true;
        this.content = template.toString();
    }

    public void addRecipient(String recipient) {
        try {
            recipients.add(InternetAddress.parse(recipient)[0]);
        } catch (AddressException e) {
            e.printStackTrace();
        }
    }

    public String getContent() {
        return content;
    }

    public String getSubject() {
        return subject;
    }

    public Address[] getRecipients() {
        Address[] returnRecipients = new Address[recipients.size()];
        int index = 0;
        for (Address address : recipients) {
            returnRecipients[index++] = address;
        }
        return returnRecipients;
    }
}
