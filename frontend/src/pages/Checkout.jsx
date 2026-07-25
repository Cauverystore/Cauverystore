import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import "../styles/checkout.css";

const STEPS = ["Delivery", "Payment", "Review", "Confirm"];

const Checkout = () => {
  const [step, setStep] = useState(0);
  const [cartItems, setCartItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState("");
  const [promoCode, setPromoCode] = useState("");
  const [promoApplied, setPromoApplied] = useState(null);
  const [promoError, setPromoError] = useState("");
  const [savedAddresses, setSavedAddresses] = useState([]);
  const [selectedAddrId, setSelectedAddrId] = useState(null);
  const [showNewAddr, setShowNewAddr] = useState(false);
  const [emiOption, setEmiOption] = useState(false);
  const [giftWrap, setGiftWrap] = useState(false);

  const [shipping, setShipping] = useState({
    name: "", phone: "", street: "", city: "", state: "", pincode: ""
  });

  const [paymentMethod, setPaymentMethod] = useState("COD");
  const navigate = useNavigate();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [cartRes, addrRes] = await Promise.all([
          api.get("/api/cart"),
          api.get("/api/user/addresses").catch(() => ({ data: [] }))
        ]);
        setCartItems(cartRes.data?.items || []);
        const addrs = Array.isArray(addrRes.data) ? addrRes.data : [];
        setSavedAddresses(addrs);
        if (addrs.length > 0) {
          setSelectedAddrId(addrs[0].id);
          setShipping(addrs[0]);
        }
      } catch (err) {
            void err;
        setCartItems([]);
      }
      setLoading(false);
    };
    fetchData();
  }, []);

  const handleChange = (e) => {
    setShipping({ ...shipping, [e.target.name]: e.target.value });
  };

  const subtotal = cartItems.reduce((sum, item) => {
    const price = item.product?.price || item.price || 0;
    return sum + price * (item.quantity || 1);
  }, 0);

  const discount = promoApplied
    ? (promoApplied.type === "PERCENTAGE"
      ? subtotal * (promoApplied.value / 100)
      : promoApplied.value)
    : 0;
  const delivery = subtotal >= 500 ? 0 : 40;
  const giftCharge = giftWrap ? 49 : 0;
  const taxableAmount = subtotal - discount;
  const tax = Math.round(Math.max(taxableAmount, 0) * 0.12 * 100) / 100;
  const total = Math.max(subtotal - discount + delivery + giftCharge + tax, 0);

  const validateStep = () => {
    if (step === 0) {
      if (!shipping.name?.trim()) return "Full name is required";
      if (!shipping.phone?.trim() || shipping.phone.length < 10) return "Valid phone number is required";
      if (!shipping.street?.trim()) return "Address is required";
      if (!shipping.city?.trim()) return "City is required";
      if (!shipping.state?.trim()) return "State is required";
      if (!shipping.pincode?.trim() || shipping.pincode.length < 5) return "Valid pincode is required";
    }
    return null;
  };

  const nextStep = () => {
    const err = validateStep();
    if (err) { setError(err); return; }
    setError("");
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };

  const prevStep = () => setStep((s) => Math.max(s - 1, 0));

  const applyPromo = async () => {
    setPromoError("");
    if (!promoCode.trim()) { setPromoError("Enter a promo code"); return; }
    try {
      const res = await api.post("/api/promo/validate", { code: promoCode.trim() });
      setPromoApplied(res.data?.promo || res.data);
      setPromoError("");
    } catch (err) {
      setPromoApplied(null);
      setPromoError(err.response?.data?.message || "Invalid or expired promo code");
    }
  };

  const handlePlaceOrder = async () => {
    const payload = {
      items: cartItems.map((item) => ({
        productId: item.product?.id || item.product?._id || item.productId,
        quantity: item.quantity || 1
      })),
      ...shipping,
      paymentMethod: paymentMethod,
      subtotal, discount, delivery, total,
      ...(promoApplied && { promoCode: promoCode.trim() }),
      ...(giftWrap && { giftWrap: true })
    };

    setPlacing(true);
    setError("");

    try {
      if (paymentMethod === "COD") {
        const res = await api.post("/api/orders/place", payload);
        const orderId = res.data?.id || res.data?._id || res.data?.orderId;
        navigate(`/order-success?id=${orderId}`);
      } else {
        const paymentRes = await api.post("/api/payment/create", { amount: total, ...payload });
        const paymentData = paymentRes.data;
        if (!window.Razorpay) {
          setError("Razorpay SDK not loaded. Please try COD.");
          setPlacing(false);
          return;
        }
        const options = {
          key: paymentData.key || paymentData.razorpayKey || process.env.REACT_APP_RAZORPAY_KEY_ID || "rzp_test_XXXXXXXXXXXXXXXXX",
          amount: paymentData.amount || Math.round(total * 100),
          currency: paymentData.currency || "INR",
          name: "Cauvery Store",
          description: "Order Payment",
          order_id: paymentData.razorpayOrderId || paymentData.orderId,
          prefill: { name: shipping.name, contact: shipping.phone },
          handler: async function (response) {
            try {
              await api.post("/api/payment/verify", {
                razorpay_payment_id: response.razorpay_payment_id,
                razorpay_order_id: response.razorpay_order_id,
                razorpay_signature: response.razorpay_signature,
                orderId: paymentData.orderId
              });
              const orderRes = await api.post("/api/orders/place", {
                ...payload,
                paymentId: response.razorpay_payment_id,
                razorpayOrderId: response.razorpay_order_id
              });
              const orderId = orderRes.data?.id || orderRes.data?._id || orderRes.data?.orderId;
              navigate(`/order-success?id=${orderId}`);
            } catch (errInner) {
              setError("Payment verification failed. Please contact support.");
              setPlacing(false);
            }
          },
          modal: { ondismiss: function () { setError("Payment was cancelled."); setPlacing(false); } }
        };
        const rzp = new window.Razorpay(options);
        rzp.on("payment.failed", function (response) {
          setError("Payment failed: " + (response.error?.description || "Unknown error"));
          setPlacing(false);
        });
        rzp.open();
      }
    } catch (err) {
      setError(err.response?.data?.message || "Failed to place order. Please try again.");
      setPlacing(false);
    }
  };

  if (loading) {
    return <div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>Loading checkout...</div>;
  }

  const renderStepIndicator = () => (
    <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginBottom: "1.5rem", flexWrap: "wrap" }}>
      {STEPS.map((label, i) => (
        <div key={label} style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
          <div style={{
            width: 32, height: 32, borderRadius: "50%", display: "flex", alignItems: "center",
            justifyContent: "center", fontWeight: 700, fontSize: "0.85rem",
            background: i <= step ? "var(--color-primary)" : "var(--color-gray-200)",
            color: i <= step ? "#fff" : "var(--color-text-secondary)"
          }}>{i + 1}</div>
          <span style={{
            fontSize: "0.85rem", fontWeight: i === step ? 600 : 400,
            color: i === step ? "var(--color-primary)" : "var(--color-text-secondary)"
          }}>{label}</span>
          {i < STEPS.length - 1 && <div style={{
            width: 24, height: 2, background: i < step ? "var(--color-primary)" : "var(--color-gray-200)"
          }} />}
        </div>
      ))}
    </div>
  );

  const renderStep = () => {
    switch (step) {
      case 0:
        return (
          <div className="checkout-section" style={{
            padding: "1.25rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-md)", background: "var(--color-surface)"
          }}>
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>Delivery Address</h2>

            {savedAddresses.length > 0 && !showNewAddr && (
              <div style={{ marginBottom: "1rem" }}>
                {savedAddresses.map((addr) => (
                  <div key={addr.id} onClick={() => { setSelectedAddrId(addr.id); setShipping(addr); }}
                    style={{
                      padding: "0.75rem", border: selectedAddrId === addr.id ? "2px solid var(--color-primary)" : "1px solid var(--color-border)",
                      borderRadius: "var(--radius-sm)", marginBottom: "0.5rem", cursor: "pointer",
                      background: selectedAddrId === addr.id ? "var(--color-primary-light)" : "var(--color-surface)"
                    }}>
                    <div style={{ fontWeight: 500 }}>{addr.name || addr.fullName}</div>
                    <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>
                      {addr.street}, {addr.city}, {addr.state} - {addr.pincode}
                    </div>
                    <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>Phone: {addr.phone}</div>
                  </div>
                ))}
                <button onClick={() => setShowNewAddr(true)} style={{
                  padding: "0.5rem 1rem", border: "1px dashed var(--color-primary)", borderRadius: "var(--radius-sm)",
                  background: "transparent", color: "var(--color-primary)", cursor: "pointer", fontSize: "0.85rem", fontWeight: 500
                }}>+ Add New Address</button>
              </div>
            )}

            {(savedAddresses.length === 0 || showNewAddr) && (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                {[
                  { label: "Full Name", name: "name", placeholder: "Enter full name" },
                  { label: "Phone Number", name: "phone", placeholder: "Enter 10-digit phone number" },
                  { label: "Address", name: "street", placeholder: "Street, area, landmark" },
                  { label: "City", name: "city", placeholder: "City" },
                  { label: "State", name: "state", placeholder: "State" },
                  { label: "Pincode", name: "pincode", placeholder: "6-digit pincode" },
                ].map((field) => (
                  <div key={field.name} style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>{field.label}</label>
                    <input name={field.name} value={shipping[field.name] || ""} onChange={handleChange}
                      placeholder={field.placeholder}
                      style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none" }} />
                  </div>
                ))}
                {showNewAddr && <button onClick={() => setShowNewAddr(false)}
                  style={{ background: "none", border: "none", color: "var(--color-primary)", cursor: "pointer", fontSize: "0.85rem", textAlign: "left" }}>
                  &larr; Select saved address
                </button>}
              </div>
            )}
          </div>
        );

      case 1:
        return (
          <div className="checkout-section" style={{
            padding: "1.25rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-md)", background: "var(--color-surface)"
          }}>
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>Payment Method</h2>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              {[
                { value: "COD", label: "Cash on Delivery", desc: "Pay when you receive your order" },
                { value: "CARD", label: "Credit / Debit Card", desc: "Visa, Mastercard, Rupay" },
                { value: "UPI", label: "UPI", desc: "Google Pay, PhonePe, Paytm" },
                { value: "NET_BANKING", label: "Net Banking", desc: "All major banks" },
                { value: "WALLET", label: "Wallet", desc: "Paytm, Amazon Pay, Mobikwik" },
              ].map((opt) => (
                <label key={opt.value} onClick={() => setPaymentMethod(opt.value)} style={{
                  display: "flex", alignItems: "center", gap: "0.75rem", padding: "0.75rem",
                  border: paymentMethod === opt.value ? "2px solid var(--color-primary)" : "1px solid var(--color-border)",
                  borderRadius: "var(--radius-sm)", cursor: "pointer",
                  background: paymentMethod === opt.value ? "var(--color-primary-light)" : "var(--color-surface)"
                }}>
                  <input type="radio" name="payment" checked={paymentMethod === opt.value} readOnly />
                  <div>
                    <div style={{ fontWeight: 500 }}>{opt.label}</div>
                    <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}>{opt.desc}</div>
                  </div>
                </label>
              ))}
            </div>

            <div style={{ marginTop: "1rem", padding: "0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
                <input type="checkbox" checked={emiOption} onChange={(e) => setEmiOption(e.target.checked)} />
                <span style={{ fontWeight: 500, fontSize: "0.9rem" }}>EMI Options Available</span>
              </label>
              {emiOption && (
                <div style={{ marginTop: "0.5rem", padding: "0.5rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)", fontSize: "0.85rem" }}>
                  No Cost EMI starting from &#8377;{Math.round(total / 6).toFixed(0)}/month for 6 months
                </div>
              )}
            </div>

            <div style={{ marginTop: "0.75rem", padding: "0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
                <input type="checkbox" checked={giftWrap} onChange={(e) => setGiftWrap(e.target.checked)} />
                <span style={{ fontWeight: 500, fontSize: "0.9rem" }}>Gift Wrap (+&#8377;49)</span>
              </label>
            </div>
          </div>
        );

      case 2:
        return (
          <div className="checkout-section" style={{
            padding: "1.25rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-md)", background: "var(--color-surface)"
          }}>
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>Review Your Order</h2>
            <div style={{ marginBottom: "1rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>Shipping To</h3>
              <div style={{ fontSize: "0.9rem", color: "var(--color-text-secondary)" }}>
                {shipping.name}, {shipping.phone}<br />
                {shipping.street}, {shipping.city}, {shipping.state} - {shipping.pincode}
              </div>
            </div>
            <div style={{ marginBottom: "1rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>Payment Method</h3>
              <div style={{ fontSize: "0.9rem", color: "var(--color-text-secondary)" }}>{paymentMethod}</div>
            </div>
            <div>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>Items ({cartItems.length})</h3>
              {cartItems.map((item, idx) => {
                const product = item.product || {};
                const image = product.image || product.images?.[0] || "";
                const price = product.price || 0;
                return (
                  <div key={item.id || idx} style={{
                    display: "flex", gap: "0.75rem", marginBottom: "0.5rem",
                    paddingBottom: "0.5rem", borderBottom: idx < cartItems.length - 1 ? "1px solid var(--color-border-light)" : "none", alignItems: "center"
                  }}>
                    <CartItemImage src={image} name={product.name} width={48} height={48} className="checkout-item-img" />
                    <div style={{ flex: 1, fontSize: "0.85rem" }}>
                      <div style={{ fontWeight: 500 }}>{product.name}</div>
                      <div style={{ color: "var(--color-text-secondary)" }}>Qty: {item.quantity || 1}</div>
                    </div>
                    <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>&#8377;{(price * (item.quantity || 1)).toFixed(2)}</div>
                  </div>
                );
              })}
            </div>
          </div>
        );

      case 3:
        return (
          <div style={{ textAlign: "center", padding: "2rem" }}>
            <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>&#9989;</div>
            <h2 style={{ fontSize: "1.3rem", fontWeight: 700, marginBottom: "0.5rem" }}>Ready to Place Order</h2>
            <p style={{ color: "var(--color-text-secondary)", marginBottom: "1.5rem" }}>
              Please confirm your order of <strong>&#8377;{total.toFixed(2)}</strong>
            </p>
            {giftWrap && <p style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>Includes Gift Wrap</p>}
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="checkout-container" style={{ maxWidth: 900, margin: "0 auto", padding: "1.5rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1rem" }}>Checkout</h1>
      {renderStepIndicator()}

      {error && (
        <div style={{
          background: "#fef2f2", color: "var(--color-error)", padding: "0.75rem 1rem",
          borderRadius: "var(--radius-sm)", marginBottom: "1rem", fontSize: "0.9rem",
          display: "flex", justifyContent: "space-between", alignItems: "center"
        }}>
          <span>{error}</span>
          <button onClick={() => setError("")} style={{
            background: "none", border: "none", color: "var(--color-error)",
            cursor: "pointer", fontSize: "1rem", fontWeight: 700
          }}>x</button>
        </div>
      )}

      <div style={{ display: "flex", gap: "1.5rem", flexWrap: "wrap" }}>
        <div style={{ flex: "1 1 500px", minWidth: 0 }}>
          {renderStep()}

          <div style={{ display: "flex", justifyContent: "space-between", marginTop: "1.25rem" }}>
            <button onClick={prevStep} disabled={step === 0} style={{
              padding: "0.6rem 1.25rem", border: "1px solid var(--color-border)",
              borderRadius: "var(--radius-sm)", background: "#fff", cursor: step === 0 ? "not-allowed" : "pointer",
              fontSize: "0.9rem", fontWeight: 500, opacity: step === 0 ? 0.5 : 1
            }}>&larr; Back</button>

            {step < STEPS.length - 1 ? (
              <button onClick={nextStep} style={{
                padding: "0.6rem 1.25rem", border: "none", borderRadius: "var(--radius-sm)",
                background: "var(--color-primary)", color: "#fff", cursor: "pointer", fontSize: "0.9rem", fontWeight: 600
              }}>Continue &rarr;</button>
            ) : (
              <button onClick={handlePlaceOrder} disabled={placing}
                style={{
                  padding: "0.75rem 2rem", border: "none", borderRadius: "var(--radius-sm)",
                  background: placing ? "var(--color-gray-300)" : "var(--color-primary)", color: "#fff",
                  cursor: placing ? "not-allowed" : "pointer", fontSize: "1rem", fontWeight: 700
                }}>
                {placing ? "Placing..." : `Place Order - \u20B9${total.toFixed(2)}`}
              </button>
            )}
          </div>
        </div>

        {/* Order summary sidebar */}
        <div style={{ width: 320, flexShrink: 0, alignSelf: "flex-start" }}>
          <div style={{
            padding: "1.25rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-md)", background: "var(--color-surface)", position: "sticky", top: 20
          }}>
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>Order Summary</h2>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Subtotal</span>
              <span>&#8377;{subtotal.toFixed(2)}</span>
            </div>
            {discount > 0 && (
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
                <span style={{ color: "#16a34a" }}>Discount</span>
                <span style={{ color: "#16a34a" }}>-&#8377;{discount.toFixed(2)}</span>
              </div>
            )}
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Subtotal after discount</span>
              <span>&#8377;{(subtotal - discount).toFixed(2)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Tax (12%)</span>
              <span>&#8377;{tax.toFixed(2)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Delivery</span>
              <span>{delivery === 0 ? <span style={{ color: "#16a34a" }}>FREE</span> : `\u20B9${delivery}`}</span>
            </div>
            {giftCharge > 0 && (
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>Gift Wrap</span>
                <span>&#8377;{giftCharge}</span>
              </div>
            )}
            <div style={{
              display: "flex", justifyContent: "space-between", marginTop: "0.5rem",
              paddingTop: "0.75rem", borderTop: "1px solid var(--color-border)",
              fontWeight: 700, fontSize: "1.05rem"
            }}>
              <span>Total</span>
              <span>&#8377;{total.toFixed(2)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Checkout;
