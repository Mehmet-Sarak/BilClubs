import java.util.HashMap;
import java.util.Map;

public class Filter {
    private Map<String, Object> filterMap = new HashMap<String, Object>();

    Filter() {}

    public void addFilter(String rule, Object value) {
        filterMap.put(rule, value);
    }

    public void clear() {
        filterMap.clear();
    }

    public Map<String, Object> getMap() {
        return filterMap;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Filter{");
        int c = 0;
        int size = filterMap.size();
        for (String key : filterMap.keySet()) {
            builder.append(key);
            builder.append(": ");
            builder.append(filterMap.get(key));
            if (++c < size) builder.append(", ");
        }
        builder.append("}");
        return builder.toString();
    }
}
