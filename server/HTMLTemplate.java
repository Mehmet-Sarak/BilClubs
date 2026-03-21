import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

public class HTMLTemplate implements Cloneable {
    private File file;
    private String content;
    
    public HTMLTemplate(String fileName) {
        file = new File(fileName);
        try {
            StringBuilder stringBuilder = new StringBuilder();
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine());
                stringBuilder.append("\n");
            }
            scanner.close();
            content = stringBuilder.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void format(HashMap<String, String> formatMap) {
        while (content.contains("{{")) {
            int formatStartIndex = content.indexOf("{{");
            int formatEndIndex = content.indexOf("}}", formatStartIndex);
            String pattern = content.substring(formatStartIndex, formatEndIndex+2);
            String injectionKey = pattern.replace("{", "")
                                         .replace("}", "")
                                         .replace(" ", "");
            String injectionValue = formatMap.get(injectionKey);
            content = content.replace(pattern, injectionValue);
        }
    }

    public void format(String key, String value) {
        int formatStartIndex = 0;
        int formatEndIndex = 0;
        while (formatStartIndex > -1) {
            formatStartIndex = content.indexOf("{{", formatEndIndex);
            formatEndIndex = content.indexOf("}}", formatStartIndex);
            if (formatStartIndex < 0) break;
            String pattern = content.substring(formatStartIndex, formatEndIndex+2);
            String injectionKey = pattern.replace("{", "")
                                         .replace("}", "")
                                         .replace(" ", "");
            if (!injectionKey.equals(key)) continue;
            content = content.replace(pattern, value);
        }
    }

    public HTMLTemplate formatted(HashMap<String, String> formatMap) {
        HTMLTemplate returnTemplate;
        returnTemplate = (HTMLTemplate) this.clone();
        returnTemplate.format(formatMap);
        return returnTemplate;
    }

    public HTMLTemplate formatted(String key, String value) {
        HTMLTemplate returnTemplate;
        returnTemplate = (HTMLTemplate) this.clone();
        returnTemplate.format(key, value);
        return returnTemplate;
    }

    public String toString() {
        return content;
    }

    @Override
    public HTMLTemplate clone() {
        try {
            return (HTMLTemplate) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
