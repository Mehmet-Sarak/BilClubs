import java.util.Map;

public class Credentials {
    private String username;
    private String password;
    private boolean isValid = true;

    public Credentials(Map<String, String> environment) {
        username = environment.get("SMTP_EMAIL");
        password = environment.get("SMTP_PASSWORD");
        if (username == null || password == null) {
            isValid = false;
        }
    }


    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isValid() {
        return isValid;
    }
}
