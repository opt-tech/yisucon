package jp.ne.opt.model;

import java.io.Serializable;

public class User implements Serializable {

    private static final long serialVersionUID = 8738267729352710528L;
    private Integer userId;
    private String userName;
    private String salt;
    private String password;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername(){
        return userName;
    }

    public void setUsername(String userName){
        this.userName = userName;
    }

    public String getSalt() { return salt; }

    public void setSalt(String salt){
        this.salt = salt;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password){
        this.password = password;
    }
}
