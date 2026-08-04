import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useWishlist } from "../context/WishlistContext";
import { sendChatMessage, performChatAction } from "../services/chatService";
import "../styles/chat-widget.css";

let uid = 0;
const nextId = () => `msg-${Date.now()}-${uid++}`;

const ChatIcon = () => (
  <svg className="cw-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
  </svg>
);

const CloseIcon = () => (
  <svg className="cw-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="18" y1="6" x2="6" y2="18" />
    <line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);

const SendIcon = () => (
  <svg className="cw-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="22" y1="2" x2="11" y2="13" />
    <polygon points="22 2 15 22 11 13 2 9 22 2" />
  </svg>
);

const WELCOME = {
  id: "welcome",
  from: "bot",
  text: "Hi! I'm the Cauvery Store assistant. I can help you find products, check your cart & wishlist, track orders, and share the latest offers. What would you like to do?",
  actions: [],
  quickReplies: ["Show me best sellers", "Any offers right now?", "Track my order", "Help"],
};

const ChatWidget = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const scrollRef = useRef(null);
  const inputRef = useRef(null);
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const wishlist = useWishlist();

  const closeWidget = useCallback(() => {
    setOpen(false);
  }, []);

  const appendBot = useCallback((data) => {
    setMessages((prev) => [
      ...prev,
      {
        id: nextId(),
        from: "bot",
        text: data?.reply || "Sorry, I didn't understand that.",
        actions: data?.actions || [],
        quickReplies: data?.quickReplies || [],
      },
    ]);
  }, []);

  const scrollToBottom = () => {
    requestAnimationFrame(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      }
    });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, busy, open]);

  useEffect(() => {
    if (open && messages.length === 0) {
      setMessages([WELCOME]);
    }
    if (open) {
      const t = setTimeout(() => inputRef.current?.focus(), 50);
      return () => clearTimeout(t);
    }
  }, [open, messages.length]);

  const runServerAction = useCallback(
    async (action) => {
      setBusy(true);
      try {
        const { data } = await performChatAction(action);
        if (action.type === "ADD_TO_CART" || action.type === "REMOVE_FROM_CART" || action.type === "MOVE_TO_CART") {
          window.dispatchEvent(new Event("cart:updated"));
        }
        if (action.type === "ADD_TO_WISHLIST" || action.type === "MOVE_TO_CART") {
          wishlist.refresh();
        }
        appendBot(data);
      } catch {
        appendBot({
          reply: "Something went wrong. Please try again in a moment.",
          intent: "error",
          actions: [],
          quickReplies: [],
        });
      } finally {
        setBusy(false);
      }
    },
    [appendBot, wishlist]
  );

  const sendText = useCallback(
    async (text) => {
      if (!text || !text.trim() || busy) return;
      const trimmed = text.trim();
      setMessages((prev) => [...prev, { id: nextId(), from: "user", text: trimmed, actions: [], quickReplies: [] }]);
      setInput("");
      setBusy(true);
      try {
        const { data } = await sendChatMessage(trimmed);
        appendBot(data);
      } catch {
        appendBot({
          reply: "I had trouble reaching the server. Please try again in a moment.",
          intent: "error",
          actions: [],
          quickReplies: [],
        });
      } finally {
        setBusy(false);
      }
    },
    [busy, appendBot]
  );

  const handleAction = useCallback(
    async (action) => {
      if (busy) return;
      const type = action.type;
      switch (type) {
        case "QUICK_REPLY":
          await sendText(action.message || action.label);
          break;
        case "VIEW_PRODUCT":
          navigate(`/product/${action.productId}`);
          closeWidget();
          break;
        case "TRACK_ORDER":
          navigate(`/orders/${action.orderId}`);
          closeWidget();
          break;
        case "VIEW_CART":
          navigate("/cart");
          closeWidget();
          break;
        case "VIEW_WISHLIST":
          navigate("/wishlist");
          closeWidget();
          break;
        case "VIEW_ORDERS":
          navigate("/orders");
          closeWidget();
          break;
        case "VIEW_OFFERS":
          navigate("/offers");
          closeWidget();
          break;
        case "CHECKOUT":
          navigate("/checkout");
          closeWidget();
          break;
        case "LOGIN":
          navigate("/login");
          closeWidget();
          break;
        case "REGISTER":
          navigate("/register");
          closeWidget();
          break;
        case "CONTACT":
          navigate("/contact");
          closeWidget();
          break;
        case "SUPPORT_TICKET":
          navigate("/support-tickets");
          closeWidget();
          break;
        case "ADD_TO_CART":
        case "REMOVE_FROM_CART":
        case "MOVE_TO_CART":
        case "ADD_TO_WISHLIST":
          await runServerAction(action);
          break;
        default:
          break;
      }
    },
    [busy, navigate, closeWidget, runServerAction, sendText]
  );

  const handleSubmit = (e) => {
    e.preventDefault();
    sendText(input);
  };

  const lastBotMessage = [...messages].reverse().find((m) => m.from === "bot");

  return (
    <>
      {open && (
        <div className="cw-widget" role="dialog" aria-label="Cauvery Store assistant chat">
          <div className="cw-header">
            <div className="cw-header-info">
              <div className="cw-avatar">
                <ChatIcon />
              </div>
              <div>
                <div className="cw-title">Cauvery Store Assistant</div>
                <div className="cw-subtitle">{isAuthenticated ? "Online · here to help" : "Online · shopping help"}</div>
              </div>
            </div>
            <button className="cw-close" onClick={closeWidget} aria-label="Close chat">
              <CloseIcon />
            </button>
          </div>

          <div className="cw-body" ref={scrollRef}>
            {messages.map((m) => (
              <div key={m.id} className={`cw-row cw-${m.from}`}>
                <div className="cw-bubble">
                  <div className="cw-text">{m.text}</div>
                  {m.actions && m.actions.length > 0 && (
                    <div className="cw-actions">
                      {m.actions.map((a, i) => (
                        <button key={i} className="cw-action" onClick={() => handleAction(a)} disabled={busy}>
                          {a.label}
                        </button>
                      ))}
                    </div>
                  )}
                  {m.quickReplies && m.quickReplies.length > 0 && (
                    <div className="cw-quick">
                      {m.quickReplies.map((q, i) => (
                        <button key={i} className="cw-chip" onClick={() => handleAction({ type: "QUICK_REPLY", label: q, message: q })} disabled={busy}>
                          {q}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
            {busy && (
              <div className="cw-row cw-bot">
                <div className="cw-bubble cw-typing">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            )}
          </div>

          <form className="cw-inputbar" onSubmit={handleSubmit}>
            <input
              ref={inputRef}
              className="cw-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about products, orders, offers..."
              disabled={busy}
              aria-label="Type a message"
            />
            <button className="cw-send" type="submit" disabled={busy || !input.trim()} aria-label="Send message">
              <SendIcon />
            </button>
          </form>
        </div>
      )}

      <button className="cw-launcher" onClick={() => setOpen((o) => !o)} aria-label={open ? "Close chat" : "Open chat assistant"}>
        {open ? <CloseIcon /> : <ChatIcon />}
      </button>
    </>
  );
};

export default ChatWidget;
