package com.smartling.marketo.sdk;

import java.util.Date;

public class Email {
    private int id;
    private String name;
    private String description;
    private Date updatedAt;
    private Status status;
    private Folder folder = new Folder();
    private TextField subject = new TextField();
    private TextField fromName = new TextField();
    private String url;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
    }

    public String getSubject() {
        return subject.getValue();
    }

    public void setSubject(String subject) {
        this.subject.setValue(subject);
    }

    public String getFromName() {
        return fromName.getValue();
    }

    public void setFromName(String value) {
        fromName.setValue(value);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static class TextField {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public enum Status {
        DRAFT, APPROVED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
