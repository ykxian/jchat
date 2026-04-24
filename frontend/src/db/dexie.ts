import Dexie, { type Table } from "dexie";
import type { Conversation, Message } from "../api/types";

interface CachedConversation extends Conversation {
  cacheKey: string;
  cachedAt: string;
  userId: string;
}

interface CachedMessage extends Message {
  cacheKey: string;
  cachedAt: string;
  conversationId: string;
  userId: string;
}

class JchatDexie extends Dexie {
  conversations!: Table<CachedConversation, string>;
  messages!: Table<CachedMessage, string>;

  constructor() {
    super("jchat");

    this.version(1).stores({
      conversations: "&cacheKey,userId,id,lastMessageAt,updatedAt",
      messages: "&cacheKey,userId,conversationId,id,createdAt,[userId+conversationId]"
    });
  }
}

const db = new JchatDexie();

function conversationCacheKey(userId: string, conversationId: string) {
  return `${userId}:${conversationId}`;
}

function messageCacheKey(userId: string, conversationId: string, messageId: string) {
  return `${userId}:${conversationId}:${messageId}`;
}

function sortConversations(items: Conversation[]) {
  return [...items].sort((left, right) => {
    const leftStamp = left.lastMessageAt ?? left.updatedAt ?? left.createdAt ?? "";
    const rightStamp = right.lastMessageAt ?? right.updatedAt ?? right.createdAt ?? "";
    return rightStamp.localeCompare(leftStamp);
  });
}

function sortMessages(items: Message[]) {
  return [...items].sort((left, right) => {
    const timeDiff = (left.createdAt ?? "").localeCompare(right.createdAt ?? "");

    if (timeDiff !== 0) {
      return timeDiff;
    }

    return left.id.localeCompare(right.id);
  });
}

function toConversationRecord(userId: string, conversation: Conversation): CachedConversation {
  return {
    ...conversation,
    cacheKey: conversationCacheKey(userId, conversation.id),
    cachedAt: new Date().toISOString(),
    userId
  };
}

function toMessageRecord(
  userId: string,
  conversationId: string,
  message: Message
): CachedMessage {
  return {
    ...message,
    cacheKey: messageCacheKey(userId, conversationId, message.id),
    cachedAt: new Date().toISOString(),
    conversationId,
    userId
  };
}

function fromConversationRecord(record: CachedConversation): Conversation {
  const { cacheKey: _cacheKey, cachedAt: _cachedAt, userId: _userId, ...conversation } = record;
  return conversation;
}

function fromMessageRecord(record: CachedMessage): Message {
  const {
    cacheKey: _cacheKey,
    cachedAt: _cachedAt,
    conversationId: _conversationId,
    userId: _userId,
    ...message
  } = record;
  return message;
}

function canUseIndexedDb() {
  return typeof indexedDB !== "undefined";
}

async function safeRead<T>(fallback: T, operation: () => Promise<T>) {
  if (!canUseIndexedDb()) {
    return fallback;
  }

  try {
    return await operation();
  } catch (error) {
    console.warn("IndexedDB read failed", error);
    return fallback;
  }
}

async function safeWrite(operation: () => Promise<void>) {
  if (!canUseIndexedDb()) {
    return;
  }

  try {
    await operation();
  } catch (error) {
    console.warn("IndexedDB write failed", error);
  }
}

export const chatCache = {
  getConversation(userId: string, conversationId: string) {
    return safeRead<Conversation | null>(null, async () => {
      const record = await db.conversations.get(conversationCacheKey(userId, conversationId));
      return record ? fromConversationRecord(record) : null;
    });
  },
  listConversations(userId: string) {
    return safeRead<Conversation[]>([], async () => {
      const records = await db.conversations.where("userId").equals(userId).toArray();
      return sortConversations(records.map(fromConversationRecord));
    });
  },
  listMessages(userId: string, conversationId: string) {
    return safeRead<Message[]>([], async () => {
      const records = await db.messages
        .where("[userId+conversationId]")
        .equals([userId, conversationId])
        .toArray();
      return sortMessages(records.map(fromMessageRecord));
    });
  },
  async replaceConversations(userId: string, conversations: Conversation[]) {
    await safeWrite(async () => {
      await db.transaction("rw", db.conversations, async () => {
        await db.conversations.where("userId").equals(userId).delete();

        if (conversations.length) {
          await db.conversations.bulkPut(
            conversations.map((conversation) => toConversationRecord(userId, conversation))
          );
        }
      });
    });
  },
  async replaceMessages(userId: string, conversationId: string, messages: Message[]) {
    await safeWrite(async () => {
      await db.transaction("rw", db.messages, async () => {
        await db.messages
          .where("[userId+conversationId]")
          .equals([userId, conversationId])
          .delete();

        if (messages.length) {
          await db.messages.bulkPut(
            messages.map((message) => toMessageRecord(userId, conversationId, message))
          );
        }
      });
    });
  },
  async upsertConversation(userId: string, conversation: Conversation) {
    await safeWrite(async () => {
      await db.conversations.put(toConversationRecord(userId, conversation));
    });
  }
};
