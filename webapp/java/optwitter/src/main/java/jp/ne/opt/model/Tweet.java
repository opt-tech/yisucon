package jp.ne.opt.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Tweet implements Serializable {

    private static final long serialVersionUID = -2097410776272699622L;
    private Integer userId;
    private String text;
    private LocalDateTime createdAt;
    private String userName;
    private String html;
    private String time;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getText(){
        return text;
    }

    public void setText(String text){
        this.text = text;
    }

    public LocalDateTime getCretedAt(){
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt){
        this.createdAt = createdAt;
    }

    public String getUserName(){
        return userName;
    }

    public void setUserName(String userName){
        this.userName = userName;
    }

    public String getHtml(){
        return html;
    }

    public void setHtml(String html){
        this.html = html;
    }

    public String getTime(){
        return time;
    }

    public void setTime(String time){
        this.time = time;
    }
}