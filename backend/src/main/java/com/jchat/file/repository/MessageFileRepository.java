package com.jchat.file.repository;

import com.jchat.file.entity.MessageFile;
import com.jchat.file.entity.MessageFileId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageFileRepository extends JpaRepository<MessageFile, MessageFileId> {

    List<MessageFile> findAllByMessageIdInOrderByMessageIdAscPositionAsc(Collection<Long> messageIds);
}
