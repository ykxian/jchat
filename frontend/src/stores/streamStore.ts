import { useSyncExternalStore } from "react";

export interface ActiveStream {
  conversationId: string;
  error: string | null;
  isStreaming: boolean;
  messageId: string | null;
  requestId: string | null;
}

interface StreamState {
  abortController: AbortController | null;
  current: ActiveStream | null;
}

type Listener = () => void;
type Selector<T> = (state: StreamState) => T;

const listeners = new Set<Listener>();

let state: StreamState = {
  abortController: null,
  current: null
};

function notify() {
  for (const listener of listeners) {
    listener();
  }
}

function setState(nextState: StreamState) {
  state = nextState;
  notify();
}

function updateState(updater: (current: StreamState) => StreamState) {
  setState(updater(state));
}

function subscribe(listener: Listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export const streamStore = {
  subscribe,
  getState() {
    return state;
  },
  start(conversationId: string, abortController: AbortController) {
    updateState(() => ({
      abortController,
      current: {
        conversationId,
        error: null,
        isStreaming: true,
        messageId: null,
        requestId: null
      }
    }));
  },
  attachMetadata(messageId: string | null, requestId: string | null) {
    updateState((current) => ({
      ...current,
      current: current.current
        ? {
            ...current.current,
            messageId,
            requestId
          }
        : null
    }));
  },
  fail(error: string) {
    updateState((current) => ({
      abortController: null,
      current: current.current
        ? {
            ...current.current,
            error,
            isStreaming: false
          }
        : null
    }));
  },
  finish() {
    updateState((current) => ({
      abortController: null,
      current: current.current
        ? {
            ...current.current,
            error: null,
            isStreaming: false
          }
        : null
    }));
  },
  clear() {
    setState({
      abortController: null,
      current: null
    });
  },
  abort() {
    state.abortController?.abort();
    updateState((current) => ({
      abortController: null,
      current: current.current
        ? {
            ...current.current,
            error: "Request aborted",
            isStreaming: false
          }
        : null
    }));
  }
};

export function useStreamStore<T>(selector: Selector<T>) {
  return useSyncExternalStore(streamStore.subscribe, () => selector(state), () => selector(state));
}
