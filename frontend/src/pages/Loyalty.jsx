import React, { useState, useEffect } from "react";
import userService from "../services/userService";
import "../styles/loyalty.css";

const Loyalty = () => {
  const [loyalty, setLoyalty] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => { const fetch = async () => { try { const res = await userService.getLoyaltyPoints(); setLoyalty(res.data); } catch (err) { void err; } setLoading(false); }; fetch(); }, []);

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  const points = loyalty?.points || 0;
  const tier = points >= 1000 ? "platinum" : points >= 500 ? "gold" : "silver";

  return (
    <div className="loyalty-page">
      <div className="loyalty-card">
        <div className="loyalty-label">Your Loyalty Points</div>
        <div className="loyalty-points">{points}</div>
        <div className={`loyalty-tier tier-${tier}`}>{tier.toUpperCase()} Member</div>
      </div>
      {(loyalty?.transactions || []).length > 0 && (
        <div className="loyalty-history">
          <h3>Recent Activity</h3>
          {loyalty.transactions.map((t, i) => (
            <div key={i} className="loyalty-transaction">
              <div><div style={{ fontWeight: 500, fontSize: "0.9rem" }}>{t.description}</div><div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>{new Date(t.date).toLocaleDateString()}</div></div>
              <div className={`transaction-amount ${t.type === "earned" ? "earned" : "redeemed"}`}>{t.type === "earned" ? "+" : "-"}{t.points} pts</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
export default Loyalty;
