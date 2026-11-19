import api from "../api/axios";

const RETRY_DELAY_MS = 1500;

const isNetworkError = (err) =>
  !err?.response || err.code === "ECONNABORTED" || /network error|timeout/i.test(err.message || "");

const withRetry = async (request) => {
  try {
    return await request();
  } catch (err) {
    if (!isNetworkError(err)) throw err;
    await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
    return request();
  }
};

// history: earlier turns [{ role: "user" | "assistant", text }], so the assistant can follow the conversation.
// context.productIds: products in the chat's last list, so "add the second one" works.
export const sendChatMessage = (message, history = [], context = {}) =>
  withRetry(() => api.post("/api/chat", { message, history, context }));

export const performChatAction = (payload) => withRetry(() => api.post("/api/chat/action", { action: payload }));

const chatService = { sendChatMessage, performChatAction };
export default chatService;
