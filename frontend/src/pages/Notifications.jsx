import React, { useState, useEffect } from "react";
import { Bell } from "lucide-react";
import userService from "../services/userService";
import "../styles/notifications.css";

const Notifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { const fetch = async () => { try { const res = await userService.getNotifications(); setNotifications(res.data || []); } catch (err) { void err; } setLoading(false); }; fetch(); }, []);

  const markRead = async (id) => {
    try { await userService.markNotificationRead(id); setNotifications(notifications.map(n => (n.id || n._id) === id ? { ...n, read: true } : n)); } catch (err) { void err; }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div className="notifications-page">
      <h1>Notifications</h1>
      {notifications.length === 0 ? <div style={{ textAlign: "center", color: "#475569" }}>No notifications</div> : notifications.map((n) => (
        <div key={n.id || n._id} className={`notification-item${n.read ? "" : " unread"}`} onClick={() => !n.read && markRead(n.id || n._id)}>
          <div className="notification-icon"><Bell size={20} /></div>
          <div className="notification-content">
            <div className="notification-title">{n.title || n.type}</div>
            <div className="notification-message">{n.message}</div>
            <div className="notification-time">{new Date(n.createdAt).toLocaleString()}</div>
          </div>
        </div>
      ))}
    </div>
  );
};
export default Notifications;
