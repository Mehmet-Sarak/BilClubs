import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

public class DBManager {

    private boolean initialized = false;

    private EntityManagerFactory userManagerFactory = null;
    private EntityManager userManager;

    private EntityManagerFactory fileManagerFactory = null;
    private EntityManager fileManager;

    public void initialize(String directory) {
        if (initialized) return;
        String userPersistence = String.format("objectdb:%s/users.odb", directory);
        String filePersistence = String.format("objectdb:%s/static.odb", directory);
        userManagerFactory = Persistence.createEntityManagerFactory(userPersistence);
        userManager = userManagerFactory.createEntityManager();
        fileManagerFactory = Persistence.createEntityManagerFactory(filePersistence);
        fileManager = fileManagerFactory.createEntityManager();
        initialized = true;
    }

    public boolean doesUniqueUserExist(Filter filter) {
        List<User> queriedUsers = queryUsers(filter);
        return (queriedUsers.size() == 1);
    }

    public List<User> queryUsers(Filter filter) {
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Queried users for filter: %s\n", filter.toString());
        if(!initialized) return null;
        Map<String, String> keyMap = new HashMap<String, String>() {{
            put("id", "u.getId()");
            put("token", "u.getToken()");
            put("name", "u.getFullName()");
            put("email", "u.getEmail()");
        }};
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT u FROM User u ");
        boolean hasFilter = false;
        Map<String, Object> filterMap = filter.getMap();
        
        for (String key : filterMap.keySet()) {
            String format = keyMap.get(key);
            if (format == null) continue;
            if(!hasFilter) {
                queryBuilder.append("WHERE ");
                hasFilter = true;
            } else {
                queryBuilder.append("AND ");
            }
            queryBuilder.append(format);
            queryBuilder.append(" = :");
            queryBuilder.append(key);
            queryBuilder.append(" ");
        }

        String queryString = queryBuilder.toString().trim();
        TypedQuery<User> query = userManager.createQuery(queryString,User.class);

        for (String key : filterMap.keySet()) {
            if (!keyMap.containsKey(key)) continue;
            query.setParameter(key, filterMap.get(key));
        }

        List<User> results = query.getResultList();
        return results;
    }

    public User queryUser(Filter filter) {
        if(!initialized) return null;
        List<User> users = queryUsers(filter);
        if (users.size() != 1) {
            if (ServerConfig.PRINT_DEBUG) System.out.printf("Info: %s returned %s results.\n", filter, users.size());
            return null;
        }
        return users.get(0);
    }

    public boolean addUser(User user) {
        if(!initialized) return false;
        Filter emailFilter = new Filter();
        emailFilter.addFilter("email", user.getEmail());
        if (queryUser(emailFilter) != null) return false; // prevent creation of a new account with the same email
        userManager.getTransaction().begin();
        userManager.persist(user);
        userManager.getTransaction().commit();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added user: %s\n", user.toString());
        return true;
    }

    public boolean addUserUnsafe(User user) {
        if(!initialized) return false;
        userManager.getTransaction().begin();
        userManager.persist(user);
        userManager.getTransaction().commit();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added user: %s\n", user.toString());
        return true;
    }

    public boolean updateUser(User user) {
        if(!initialized) return false;
        if(user.getId() == null) return false;
        userManager.getTransaction().begin();
        User queriedUser = userManager.find(User.class, user.getId());
        if (queriedUser == null) return false; // prevent creation of a new account with the same email
        userManager.merge(user);
        userManager.getTransaction().commit();
        // userManager.refresh(queriedUser);
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Updated user: %s\n", user.toString());
        return true;
    }


    // Files

    public boolean addFile(Media media) {
        if(!initialized) return false;
        fileManager.getTransaction().begin();
        fileManager.persist(media);
        fileManager.getTransaction().commit();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added file: %s\n", media.toString());
        return true;
    }

    public List<Media> queryFiles(Filter filter) {
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Queried files for filter: %s\n", filter.toString());
        if(!initialized) return null;
        Map<String, String> keyMap = new HashMap<String, String>() {{
            put("id", "u.getId()");
            put("realName", "u.getRealFileName()");
            put("storedName", "u.getStoredFileName()");
        }};
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT u FROM Media u ");
        boolean hasFilter = false;
        Map<String, Object> filterMap = filter.getMap();
        
        for (String key : filterMap.keySet()) {
            String format = keyMap.get(key);
            if (format == null) continue;
            if(!hasFilter) {
                queryBuilder.append("WHERE ");
                hasFilter = true;
            } else {
                queryBuilder.append("AND ");
            }
            queryBuilder.append(format);
            queryBuilder.append(" = :");
            queryBuilder.append(key);
            queryBuilder.append(" ");
        }

        String queryString = queryBuilder.toString().trim();
        TypedQuery<Media> query = fileManager.createQuery(queryString,Media.class);

        for (String key : filterMap.keySet()) {
            if (!keyMap.containsKey(key)) continue;
            query.setParameter(key, filterMap.get(key));
        }

        List<Media> results = query.getResultList();
        return results;
    }

    public Media queryFile(Filter filter) {
        if(!initialized) return null;
        List<Media> files = queryFiles(filter);
        if (files.size() != 1) {
            if (ServerConfig.PRINT_DEBUG) System.out.printf("Info: %s returned %s results.\n", filter, files.size());
            return null;
        }
        return files.get(0);
    }
}