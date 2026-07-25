import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import promoService from "../services/promoService";

const OffersPage = () => {
  const { offerId } = useParams();
  const [offers, setOffers] = useState([]);
  const [offer, setOffer] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      try {
        if (offerId) { const res = await promoService.getOfferById(offerId); setOffer(res.data); }
        else { const res = await promoService.getActiveOffers(); setOffers(res.data || []); }
      } catch (err) {             void err; }
      setLoading(false);
    };
    fetch();
  }, [offerId]);

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  if (offer) return (
    <div style={{ maxWidth: "800px", margin: "0 auto", padding: "1.5rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1rem" }}>{offer.title}</h1>
      <div style={{ padding: "1.5rem", border: "1px solid #e2e8f0", borderRadius: 10, background: "#fff" }}>
        <p style={{ fontSize: "1.2rem", color: "#16a34a", fontWeight: 600 }}>{offer.description}</p>
        {offer.discountPercent && <p style={{ fontSize: "1.5rem", fontWeight: 700 }}>{offer.discountPercent}% OFF</p>}
        <p style={{ color: "#475569" }}>Code: <strong>{offer.code}</strong></p>
        <p style={{ color: "#94a3b8", fontSize: "0.85rem" }}>Valid till: {new Date(offer.validTill).toLocaleDateString()}</p>
      </div>
      <Link to="/products" style={{ display: "inline-block", marginTop: "1rem", color: "#16a34a" }}>&larr; Shop Now</Link>
    </div>
  );

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto", padding: "1.5rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Active Offers</h1>
      {offers.length === 0 ? <p style={{ color: "#475569" }}>No active offers at the moment.</p> : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1.25rem" }}>
          {offers.map((o) => (
            <Link key={o.id || o._id} to={`/offers/${o.id || o._id}`} style={{ textDecoration: "none" }}>
              <div style={{ padding: "1.5rem", border: "1px solid #e2e8f0", borderRadius: 10, background: "#fff", transition: "box-shadow 0.2s" }}>
                <h3 style={{ marginBottom: "0.5rem", color: "#0f172a" }}>{o.title}</h3>
                <p style={{ color: "#16a34a", fontWeight: 600 }}>{o.description}</p>
                <p style={{ color: "#475569", fontSize: "0.9rem" }}>Use Code: <strong>{o.code}</strong></p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};
export default OffersPage;
