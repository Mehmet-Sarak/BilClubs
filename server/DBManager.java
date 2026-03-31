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
    private EntityManagerFactory clubManagerFactory = null;
    private EntityManagerFactory fileManagerFactory = null;

    public void initialize(String directory) {
        if (initialized) return;
        String userPersistence = String.format("objectdb:%s/users.odb", directory);
        String clubPersistence = String.format("objectdb:%s/clubs.odb", directory);
        String filePersistence = String.format("objectdb:%s/static.odb", directory);
        userManagerFactory = Persistence.createEntityManagerFactory(userPersistence);
        clubManagerFactory = Persistence.createEntityManagerFactory(clubPersistence);
        fileManagerFactory = Persistence.createEntityManagerFactory(filePersistence);
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
        EntityManager userManager = userManagerFactory.createEntityManager();
        TypedQuery<User> query = userManager.createQuery(queryString,User.class);
        for (String key : filterMap.keySet()) {
            if (!keyMap.containsKey(key)) continue;
            query.setParameter(key, filterMap.get(key));
        }
        List<User> results = query.getResultList();
        userManager.close();
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
        EntityManager userManager = userManagerFactory.createEntityManager();
        userManager.getTransaction().begin();
        userManager.persist(user);
        userManager.getTransaction().commit();
        userManager.close();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added user: %s\n", user.toString());
        return true;
    }

    public boolean addUserUnsafe(User user) {
        if(!initialized) return false;
        EntityManager userManager = userManagerFactory.createEntityManager();
        userManager.getTransaction().begin();
        userManager.persist(user);
        userManager.getTransaction().commit();
        userManager.close();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added user: %s\n", user.toString());
        return true;
    }

    public boolean updateUser(User user) {
        if(user == null || user.getId() == null || !initialized) return false;
        EntityManager userManager = userManagerFactory.createEntityManager();
        userManager.getTransaction().begin();
        User queriedUser = userManager.find(User.class, user.getId());
        if (queriedUser == null) return false; // prevent creation of a new account with the same email
        userManager.merge(user);
        userManager.getTransaction().commit();
        userManager.close();
        // userManager.refresh(queriedUser);
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Updated user: %s\n", user.toString());
        return true;
    }

    public boolean addClub(Club club){
        if (club == null || !initialized) return false;
        EntityManager clubManager = clubManagerFactory.createEntityManager();
        clubManager.getTransaction().begin();
        clubManager.persist(club);
        clubManager.getTransaction().commit();
        clubManager.close();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Added club: %s\n", club.toString());
        return true;
    }

    public boolean updateClub(Club club){
        if (club == null || club.getId() == null || !initialized) return false;
        EntityManager clubManager = clubManagerFactory.createEntityManager();
        clubManager.getTransaction().begin();
        Club queriedClub = clubManager.find(Club.class, club.getId());
        if (queriedClub == null) return false; // To prevent creation of a duplicate club. Maybe. I am just adapting Ozan's code to a new class to be honest...
        clubManager.merge(club);
        clubManager.getTransaction().commit();
        clubManager.close();
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Updated club: %s\n", club.toString());
        return true;
    }

    public List<Club> queryClubs(Filter filter) {
        if (ServerConfig.PRINT_DEBUG) System.out.printf("Queried clubs for filter: %s\n", filter.toString());
        if(!initialized) return null;
        Map<String, String> keyMap = new HashMap<String, String>() {{
            put("id", "c.getId()");
            put("name", "c.getClubName()");
        }};
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT c FROM Club c ");
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
        EntityManager clubManager = clubManagerFactory.createEntityManager();
        TypedQuery<Club> query = clubManager.createQuery(queryString,Club.class);
        for (String key : filterMap.keySet()) {
            if (!keyMap.containsKey(key)) continue;
            query.setParameter(key, filterMap.get(key));
        }
        List<Club> results = query.getResultList();
        clubManager.close();
        return results;
    }

    public Club queryClub(Filter filter) {
        if(!initialized) return null;
        List<Club> clubs = queryClubs(filter);
        if (clubs.size() != 1) {
            if (ServerConfig.PRINT_DEBUG) System.out.printf("Info: %s returned %s results.\n", filter, clubs.size());
            return null;
        }
        return clubs.get(0);
    }

    // Interactions between Users and Clubs
    // TODO: Literally everything about Events

    public boolean addUserToClub(User newMember, Club club, User manager){
        if (newMember.isBannedFromClub(club) || !manager.hasClubPrivilege(club, Privileges.MANAGER)) return false;
        club.addMember(newMember);
        newMember.joinClub(club);
        updateUser(newMember);
        updateClub(club);
        return true;
    }

    public boolean removeUserFromClub(User member, Club club){
        if (member.isBannedFromClub(club)) return false; // User privileges are erased when they leave a club, therefore banned users should be prevented from leaving
        club.removeMember(member);
        member.leaveClub(club);
        updateUser(member);
        updateClub(club);
        return true;
    }

    public boolean setUserClubPrivilege(User member, Club club, int privilege, User manager){
        if (!manager.hasClubPrivilege(club, Privileges.MANAGER)) return false;
        member.setClubPrivilege(club, privilege);
        club.setMemberPrivilege(member, privilege);
        updateUser(member);
        updateClub(club);
        return true;
    }

    // Files

    public boolean addFile(Media media) {
        if(!initialized) return false;
        EntityManager fileManager = fileManagerFactory.createEntityManager();
        fileManager.getTransaction().begin();
        fileManager.persist(media);
        fileManager.getTransaction().commit();
        fileManager.close();
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
        EntityManager fileManager = fileManagerFactory.createEntityManager();
        TypedQuery<Media> query = fileManager.createQuery(queryString,Media.class);
        for (String key : filterMap.keySet()) {
            if (!keyMap.containsKey(key)) continue;
            query.setParameter(key, filterMap.get(key));
        }
        List<Media> results = query.getResultList();
        fileManager.close();
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