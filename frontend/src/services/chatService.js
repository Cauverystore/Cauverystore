import api from "../api/axios";

export const sendChatMessage = (message) =>
  api.post("/api/chat", { message });

export const performChatAction = (payload) =>
  api.post("/api/chat/action", { action: payload });

const chatService = { sendChatMessage, performChatAction };
export default chatService;
