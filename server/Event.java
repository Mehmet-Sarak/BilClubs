import java.time.LocalDateTime;
import java.util.ArrayList;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Event {
    @Id 
    @GeneratedValue 
    private Integer id;
    private String name;
    private String description;
    private String location;
    private LocalDateTime start;
    private LocalDateTime end;
    private Integer quota;
    private ArrayList<User> registeredUsers = null;
    private Club club;

    public Event(){
    }

    public Event(String argName, Club argClub, String argDescription, String argPlace, LocalDateTime argStart, LocalDateTime argEnd, Integer argQuota){
        name = argName;
        description = argDescription;
        club = argClub;
        location = argPlace;
        start = argStart;
        end = argEnd;
        quota = argQuota; // set to null for infinite quota
        registeredUsers = new ArrayList<>();
    }

    public boolean isOpen(){
        return registeredUsers.size() != quota && LocalDateTime.now().isBefore(start);
    }

    public boolean conflictsWith(Event other){
        LocalDateTime otherStart = other.getStart();
        LocalDateTime otherEnd = other.getEnd();
        return !(end.isBefore(otherStart) && otherEnd.isBefore(start));
    }

    public void registerUser(User user){
        registeredUsers.add(user);
    }

    public void removeUser(User user){
        registeredUsers.remove(user);
    }

    public Integer getId(){
        return id;
    }

    public String getEventName(){
        return name;
    }

    public String getDescription(){
        return description;
    }

    public Club getClub(){
        return club;
    }

    public String getLocation(){
        return location;
    }

    public LocalDateTime getStart(){
        return start;
    }

    public LocalDateTime getEnd(){
        return end;
    }

    public int getQuota(){
        return quota;
    }

    public void setName(String argName){
        name = argName;
    }

    public void setDescription(String argDescription){
        name = argDescription;
    }

    public void setLocation(String argLocation){
        location = argLocation;
    }

    public void setStartAndEnd(LocalDateTime argStart, LocalDateTime argEnd){
        start = argStart;
        end = argEnd;
    }

    public boolean setQuta(int argQuota){
        if (quota < registeredUsers.size()) return false;
        quota = argQuota;
        return true;
    }

    public String toString(){
        return "Club ID: " + id + "named " + name;
    }
}
