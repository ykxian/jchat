package com.jchat.file.entity;

import java.io.Serializable;
import java.util.Objects;

public class MessageFileId implements Serializable {

    private Long messageId;
    private Long fileId;

    public MessageFileId() {
    }

    public MessageFileId(Long messageId, Long fileId) {
        this.messageId = messageId;
        this.fileId = fileId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MessageFileId that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId) && Objects.equals(fileId, that.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, fileId);
    }
}
