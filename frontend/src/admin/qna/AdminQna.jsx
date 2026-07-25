import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const AdminQna = () => {
  const [qnas, setQnas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [answerText, setAnswerText] = useState({});

  useEffect(() => {
    const fetch = async () => { try { const res = await api.get("/api/admin/qna"); setQnas(res.data || []); } catch (err) { console.error(err); } setLoading(false); };
    fetch();
  }, []);

  const handleAnswer = async (qnaId) => {
    try { await api.put(`/api/admin/qna/${qnaId}/answer`, { answer: answerText[qnaId] }); setAnswerText({ ...answerText, [qnaId]: "" }); const res = await api.get("/api/admin/qna"); setQnas(res.data || []); } catch (err) { alert("Failed to submit answer"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Q&A Management</h1>
      {qnas.length === 0 ? <p style={{ color: "#475569" }}>No questions yet</p> : qnas.map((q) => (
        <div key={q.id || q._id} style={{ padding: "1rem", border: "1px solid #e2e8f0", borderRadius: 8, marginBottom: "0.75rem", background: "#fff" }}>
          <div style={{ fontWeight: 500 }}>Q: {q.question}</div>
          <div style={{ color: "#475569", fontSize: "0.85rem" }}>Product: {q.product?.name} | By: {q.user?.name || q.userName}</div>
          {q.answer ? <div style={{ marginTop: "0.5rem", color: "#16a34a", fontWeight: 500 }}>A: {q.answer}</div> : (
            <div style={{ marginTop: "0.5rem", display: "flex", gap: "0.5rem" }}>
              <input value={answerText[q.id || q._id] || ""} onChange={(e) => setAnswerText({ ...answerText, [q.id || q._id]: e.target.value })} placeholder="Write answer..." style={{ flex: 1, padding: "0.4rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 4, fontSize: "0.85rem" }} />
              <button onClick={() => handleAnswer(q.id || q._id)} style={{ padding: "0.4rem 1rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 4, cursor: "pointer" }}>Answer</button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};
export default AdminQna;
