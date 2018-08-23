package jp.ne.opt.model;

import java.io.Serializable;

public class Friends implements Serializable {
    private static final long serialVersionUID = 1426112632693066191L;
    private Integer userId;
    private String userName;
    private String friends;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

   public String getUserName(){
       return userName;
   }

   public void setUserName(String userName){
       this.userName = userName;
   }

   public String getFriends() {
       return friends;
   }

   public void setFriends(String friends) {
       this.friends = friends;
   }
}
