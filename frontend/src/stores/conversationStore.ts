import { useSyncExternalStore } from "react";
import type { Conversation, Message } from "../api/types";

interface ConversationState {
  currentId: string | null;
  currentMessagesLoaded: boolean;
  isCreatingConversation: boolean;
  isLoadingMessages: boolean;
  isLoadingList: boolean;
  items: Conversation[];
  messagesByConversation: Record<string, Message[]>;
}

type Listener = () => void;
type Selector<T> = (state: ConversationState) => T;

const listeners = new Set<Listener>();

let state: ConversationState = {
  currentId: null,
  currentMessagesLoaded: false,
  isCreatingConversation: false,
  isLoadingMessages: false,
  isLoadingList: false,
  items: [],
  messagesByConversation: {}
};

function notify() {
  for (const listener of listeners) {
    listener();
  }
}

function setState(nextState: ConversationState) {
  state = nextState;
  notify();
}

function updateState(updater: (current: ConversationState) => ConversationState) {
  setState(updater(state));
}

function upsertConversationList(items: Conversation[], existing: Conversation[]) {
  const seen = new Set(items.map((item) => item.id));
  const merged = [...items];

  for (const item of existing) {
    if (!seen.has(item.id)) {
      merged.push(item);
    }
  }

  return merged;
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

function subscribe(listener: Listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getState() {
  return state;
}

export const conversationStore = {
  subscribe,
  getState,
  setCurrent(currentId: string | null) {
    updateState((current) => ({
      ...current,
      currentId,
      currentMessagesLoaded: currentId ? Boolean(current.messagesByConversation[currentId]) : false
    }));
  },
  setLoadingList(isLoadingList: boolean) {
    updateState((current) => ({
      ...current,
      isLoadingList
    }));
  },
  setCreatingConversation(isCreatingConversation: boolean) {
    updateState((current) => ({
      ...current,
      isCreatingConversation
    }));
  },
  setLoadingMessages(isLoadingMessages: boolean) {
    updateState((current) => ({
      ...current,
      isLoadingMessages
    }));
  },
  setConversations(items: Conversation[]) {
    updateState((current) => ({
      ...current,
      items: sortConversations(upsertConversationList(items, current.items))
    }));
  },
  upsertConversation(item: Conversation) {
    updateState((current) => ({
      ...current,
      items: sortConversations(
        upsertConversationList(
          [item],
          current.items.filter((conversation) => conversation.id !== item.id)
        )
      )
    }));
  },
  setMessages(conversationId: string, messages: Message[]) {
    updateState((current) => ({
      ...current,
      currentMessagesLoaded: current.currentId === conversationId,
      messagesByConversation: {
        ...current.messagesByConversation,
        [conversationId]: sortMessages(messages)
      }
    }));
  },
  addMessage(conversationId: string, message: Message) {
    updateState((current) => ({
      ...current,
      currentMessagesLoaded: current.currentId === conversationId,
      messagesByConversation: {
        ...current.messagesByConversation,
        [conversationId]: sortMessages([
          ...(current.messagesByConversation[conversationId] ?? []),
          message
        ])
      }
    }));
  },
  updateMessage(conversationId: string, messageId: string, patch: Partial<Message>) {
    updateState((current) => ({
      ...current,
      messagesByConversation: {
        ...current.messagesByConversation,
        [conversationId]: sortMessages(
          (current.messagesByConversation[conversationId] ?? []).map((message) =>
            message.id === messageId ? { ...message, ...patch } : message
          )
        )
      }
    }));
  }
};

export function useConversationStore<T>(selector: Selector<T>) {
  return useSyncExternalStore(
    conversationStore.subscribe,
    () => selector(state),
    () => selector(state)
  );
}
