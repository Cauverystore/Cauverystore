import React, { useState, useEffect } from "react";
import { variantLabel } from "../utils/variant";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import loadRazorpay from "../utils/loadRazorpay";
import { trackBeginCheckout } from "../utils/analytics";
import "../styles/checkout.css";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const STEPS = ["Delivery", "Payment", "Review", "Confirm"];
// Shown on the review step, so the customer reads "Cash on Delivery" rather than "COD".
const PAYMENT_LABELS = { get COD() { return i18n.t("msgs.payCod"); }, get CARD() { return i18n.t("msgs.payCard"); }, UPI: "UPI", get NET_BANKING() { return i18n.t("msgs.payNetBanking"); }, get WALLET() { return i18n.t("msgs.payWallet"); } };

const Checkout = () => {
  const {
    t: tr
  } = useTranslation();

  const [step, setStep] = useState(0);
  const [cartItems, setCartItems] = useState([]);
  const [serverTax, setServerTax] = useState(null);
  const [bill, setBill] = useState(null);
  const [billLoading, setBillLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState("");
  const [promoCode, setPromoCode] = useState("");
  const [promoApplied, setPromoApplied] = useState(null);
  const [promoError, setPromoError] = useState("");
  const [savedAddresses, setSavedAddresses] = useState([]);
  const [selectedAddrId, setSelectedAddrId] = useState(null);
  const [showNewAddr, setShowNewAddr] = useState(false);
  const [giftWrap, setGiftWrap] = useState(false);
  const [b2b, setB2b] = useState(false);
  const [gstin, setGstin] = useState("");
  const [buyerLegalName, setBuyerLegalName] = useState("");
  // Section 10(1)(b): the buyer is having the goods delivered to somebody else. It moves the
  // place of supply to the buyer's own registered state, which changes whether the invoice
  // carries IGST or CGST+SGST - so it has to be declared, never inferred from the addresses.
  const [billToShipTo, setBillToShipTo] = useState(false);
  const [consigneeName, setConsigneeName] = useState("");
  const [consigneeAddress, setConsigneeAddress] = useState("");

  const [shipping, setShipping] = useState({
    fullName: "", phone: "", line1: "", line2: "", street: "", city: "", state: "", pincode: "", country: "India"
  });

  const [paymentMethod, setPaymentMethod] = useState("UPI");
  const navigate = useNavigate();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [cartRes, addrRes] = await Promise.all([
          api.get("/api/cart"),
          api.get("/api/users/addresses").catch(() => ({ data: [] }))
        ]);
        setCartItems(cartRes.data?.items || []);
        setServerTax(typeof cartRes.data?.tax === "number" ? cartRes.data.tax : null);
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

  useEffect(() => {
    if (!loading && cartItems.length > 0) {
      trackBeginCheckout(cartItems.map((item) => ({ ...(item.product || item), quantity: item.quantity })));
    }
  }, [loading, cartItems]);

  const handleChange = (e) => {
    setShipping({ ...shipping, [e.target.name]: e.target.value });
  };

  // The itemised bill is fetched as soon as there is somewhere to deliver to, because the
  // destination decides IGST against CGST+SGST and the same goods are taxed differently either
  // way. Prices are not sent - the server reads them from the stored cart, so the tax cannot be
  // quoted against a figure the browser made up.
  useEffect(() => {
    const state = shipping.state?.trim();
    const pincode = shipping.pincode?.trim();
    if (!state && !pincode) { setBill(null); return; }
    if (cartItems.length === 0) { setBill(null); return; }

    let cancelled = false;
    setBillLoading(true);
    const t = setTimeout(() => {
      api.post("/api/checkout/bill", { state, pincode, city: shipping.city?.trim() })
        .then((r) => { if (!cancelled) setBill(r.data || null); })
        .catch(() => { if (!cancelled) setBill(null); })
        .finally(() => { if (!cancelled) setBillLoading(false); });
    }, 400);
    return () => { cancelled = true; clearTimeout(t); };
  }, [shipping.state, shipping.pincode, shipping.city, cartItems]);

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
  // GST comes from the server, worked out per item from its HSN code. There is deliberately no
  // flat-rate fallback: this used to show 12% of the basket whenever the server figure was
  // missing, which is not a rate anything is taxed at any more and quoted a total the invoice
  // would never match. If the tax cannot be worked out, the order does not proceed.
  const retained = subtotal > 0 ? Math.max(taxableAmount, 0) / subtotal : 0;
  const billTax = bill && typeof bill.totalTax === "number" ? bill.totalTax : null;
  const tax = billTax !== null
    ? Math.round(billTax * retained * 100) / 100
    : (serverTax !== null ? Math.round(serverTax * retained * 100) / 100 : null);
  const taxKnown = tax !== null;
  // Prices include GST, so the tax is inside the subtotal and is shown, not added.
  const total = Math.max(subtotal - discount + delivery + giftCharge, 0);

  const validateStep = () => {
    if (step === 0) {
      if (!shipping.fullName?.trim()) return i18n.t("msgs.fullNameRequired");
      if (!shipping.phone?.trim() || shipping.phone.length < 10) return i18n.t("msgs.phoneRequired");
      if (!shipping.line1?.trim() && !shipping.street?.trim()) return i18n.t("msgs.addressRequired");
      if (!shipping.city?.trim()) return i18n.t("msgs.cityRequired");
      if (!shipping.state?.trim()) return i18n.t("msgs.stateRequired");
      if (!shipping.pincode?.trim() || shipping.pincode.length < 5) return i18n.t("msgs.pincodeRequired");
      if (b2b) {
        const g = gstin.trim().toUpperCase();
        if (g.length !== 15) return i18n.t("msgs.gstin15");
        if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(g)) return i18n.t("msgs.gstinInvalid");
        if (!buyerLegalName.trim()) return i18n.t("msgs.legalNameRequired");
        // Rule 46(o) requires the consignee on the invoice when they are not the buyer. Caught
        // here rather than at the API, so nobody reaches the payment step and is turned back.
        if (billToShipTo && !consigneeName.trim()) {
          return i18n.t("msgs.consigneeRequired");
        }
      }
      // Only a registered buyer can declare this: the rule turns on a third person's principal
      // place of business, and an unregistered buyer has none.
      if (billToShipTo && !b2b) {
        return i18n.t("msgs.thirdPartyNeedsGstin");
      }
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
    if (!promoCode.trim()) { setPromoError(i18n.t("msgs.enterPromo")); return; }
    try {
      const res = await api.post("/api/promo/validate", { code: promoCode.trim(), cartTotal: subtotal });
      setPromoApplied(res.data?.promo || res.data);
      setPromoError("");
    } catch (err) {
      setPromoApplied(null);
      setPromoError(err.response?.data?.message || err.response?.data?.error || i18n.t("msgs.promoInvalid"));
    }
  };

  const handlePlaceOrder = async () => {
    // The server refuses to invoice goods it cannot tax, so taking payment for them would only
    // produce a payment with no lawful invoice behind it and a refund to arrange afterwards.
    if (bill && bill.canProceed === false) {
      setError(bill.message || tr("checkout.someItemsInThisOrder"));
      return;
    }
    if (!taxKnown) {
      setError(tr("checkout.theGstOnThisOrder"));
      return;
    }

    const payload = {
      items: cartItems.map((item) => ({
        productId: item.product?.id || item.product?._id || item.productId,
        quantity: item.quantity || 1
      })),
      ...shipping,
      ...(selectedAddrId && !showNewAddr ? { addressId: selectedAddrId } : {}),
      paymentMethod: paymentMethod,
      subtotal, discount, delivery, total,
      ...(promoApplied && { promoCode: promoCode.trim() }),
      ...(giftWrap && { giftWrap: true }),
      ...(b2b && gstin.trim() && { buyerGstin: gstin.trim().toUpperCase() }),
      ...(b2b && buyerLegalName.trim() && { buyerLegalName: buyerLegalName.trim() }),
      ...(b2b && billToShipTo && {
        billToShipTo: true,
        consigneeName: consigneeName.trim(),
        ...(consigneeAddress.trim() && { consigneeAddress: consigneeAddress.trim() })
      })
    };

    setPlacing(true);
    setError("");

    try {
      if (paymentMethod === "COD") {
        const res = await api.post("/api/orders/place", payload);
        const orderId = res.data?.id || res.data?._id || res.data?.orderId;
        navigate(`/order-success?id=${orderId}`);
      } else {
        let rzp;
        try {
          await loadRazorpay();
          rzp = window.Razorpay;
        } catch (e) {
          setError(tr("checkout.weCouldnTOpenThe"));
          setPlacing(false);
          return;
        }
        const paymentRes = await api.post("/api/payment/create", { amount: total, ...payload });
        const paymentData = paymentRes.data;
        // The backend is the only place that knows which Razorpay account this deployment is
        // configured against, so it is the only acceptable source for the key. There used to be a
        // chain of fallbacks ending in a literal "rzp_test_XXXXXXXXXXXXXXXXX": if the response
        // ever arrived without a key, the customer was sent to Razorpay with a made-up one and
        // got an opaque gateway error, when the actual fault was ours and on our side.
        const razorpayKey = paymentData.key || paymentData.razorpayKey;
        if (!razorpayKey) {
          setError(tr("checkout.paymentIsNotConfiguredCorrectly"));
          setPlacing(false);
          return;
        }
        const options = {
          key: razorpayKey,
          amount: paymentData.amount || Math.round(total * 100),
          currency: paymentData.currency || "INR",
          name: "Cauvery Store",
          description: tr("checkout.orderPayment"),
          order_id: paymentData.razorpayOrderId || paymentData.orderId,
          prefill: { name: shipping.fullName, contact: shipping.phone },
          handler: async function (response) {
            try {
              await api.post("/api/payment/verify", {
                razorpay_payment_id: response.razorpay_payment_id,
                razorpay_order_id: response.razorpay_order_id,
                razorpay_signature: response.razorpay_signature
              });
              const orderRes = await api.post("/api/orders/place", {
                ...payload,
                paymentId: response.razorpay_payment_id,
                razorpayOrderId: response.razorpay_order_id
              });
              const orderId = orderRes.data?.id || orderRes.data?._id || orderRes.data?.orderId;
              navigate(`/order-success?id=${orderId}`);
            } catch (errInner) {
              setError(tr("checkout.weCouldnTConfirmYour"));
              setPlacing(false);
            }
          },
          modal: { ondismiss: function () { setError(tr("checkout.paymentWasCancelledYouHave")); setPlacing(false); } }
        };
        const rzpInst = new rzp(options);
        rzpInst.on("payment.failed", function (response) {
          setError(i18n.t("msgs.paymentFailed", { reason: response.error?.description || i18n.t("msgs.unknownError") }));
          setPlacing(false);
        });
        rzpInst.open();
      }
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || tr("checkout.failedToPlaceOrderPlease"));
      setPlacing(false);
    }
  };

  if (loading) {
    return <div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>{tr("checkout.loadingCheckout")}</div>;
  }

  if (!loading && cartItems.length === 0) {
    return (
      <div style={{ textAlign: "center", padding: "3rem" }}>
        <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>&#128722;</div>
        <h2 style={{ fontSize: "1.3rem", fontWeight: 700, marginBottom: "0.5rem" }}>{tr("cart.empty")}</h2>
        <p style={{ color: "var(--color-text-secondary)", marginBottom: "1.5rem" }}>{tr("checkout.addAProductToYour")}</p>
        <button onClick={() => navigate("/")} style={{
          padding: "0.75rem 2rem", border: "none", borderRadius: "var(--radius-sm)",
          background: "var(--color-primary)", color: "#fff", cursor: "pointer", fontSize: "1rem", fontWeight: 600
        }}>{tr("cartDrawer.browseProducts")}</button>
      </div>
    );
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
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>{tr("checkout.deliveryAddress")}</h2>

            {savedAddresses.length > 0 && !showNewAddr && (
              <div style={{ marginBottom: "1rem" }}>
                {savedAddresses.map((addr) => (
                  <button type="button" key={addr.id} onClick={() => { setSelectedAddrId(addr.id); setShipping(addr); }}
                    aria-pressed={selectedAddrId === addr.id}
                    style={{
                      display: "block", width: "100%", textAlign: "left", fontFamily: "inherit",
                      padding: "0.75rem", border: selectedAddrId === addr.id ? "2px solid var(--color-primary)" : "1px solid var(--color-border)",
                      borderRadius: "var(--radius-sm)", marginBottom: "0.5rem", cursor: "pointer",
                      background: selectedAddrId === addr.id ? "var(--color-primary-light)" : "var(--color-surface)"
                    }}>
                    <div style={{ fontWeight: 500 }}>{addr.name || addr.fullName}</div>
                    <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>
                      {addr.line1 || addr.street}{addr.line2 ? `, ${addr.line2}` : ""}, {addr.city}, {addr.state} - {addr.pincode}
                    </div>
                    <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>{tr("checkout.phone")}{" "}{addr.phone}</div>
                  </button>
                ))}
                <button onClick={() => { setShowNewAddr(true); setSelectedAddrId(null); }} style={{
                  padding: "0.5rem 1rem", border: "1px dashed var(--color-primary)", borderRadius: "var(--radius-sm)",
                  background: "transparent", color: "var(--color-primary)", cursor: "pointer", fontSize: "0.85rem", fontWeight: 500
                }}>{tr("checkout.addNewAddress")}</button>
              </div>
            )}

            {(savedAddresses.length === 0 || showNewAddr) && (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                {[
                  { label: tr("checkout.fullName"), name: "fullName", placeholder: tr("checkout.enterFullName") },
                  { label: tr("checkout.phoneNumber"), name: "phone", placeholder: tr("checkout.enter10DigitPhoneNumber") },
                  { label: tr("addressBook.addressLine1"), name: "line1", placeholder: tr("checkout.houseNoBuildingStreet") },
                  { label: tr("addressBook.addressLine2"), name: "line2", placeholder: tr("addressBook.areaLandmarkOptional") },
                  { label: tr("addressBook.city"), name: "city", placeholder: tr("addressBook.city") },
                  { label: tr("addressBook.state"), name: "state", placeholder: tr("addressBook.state") },
                  { label: tr("addressBook.pincode"), name: "pincode", placeholder: tr("checkout.6DigitPincode") },
                  { label: tr("addressBook.country"), name: "country", placeholder: tr("addressBook.country") },
                ].map((field) => (
                  <div key={field.name} style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label htmlFor={`shipping-${field.name}`} style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>{field.label}</label>
                    <input id={`shipping-${field.name}`} name={field.name} value={shipping[field.name] || ""} onChange={handleChange}
                      placeholder={field.placeholder}
                      style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none" }} />
                  </div>
                ))}
                {showNewAddr && <button onClick={() => setShowNewAddr(false)}
                  style={{ background: "none", border: "none", color: "var(--color-primary)", cursor: "pointer", fontSize: "0.85rem", textAlign: "left" }}>{tr("checkout.selectSavedAddress")}</button>}
              </div>
            )}

            <div style={{ marginTop: "1.25rem", paddingTop: "1rem", borderTop: "1px solid var(--color-border-light)" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer", marginBottom: "0.75rem" }}>
                <input type="checkbox" checked={b2b} onChange={(e) => setB2b(e.target.checked)} />
                <span style={{ fontWeight: 600, fontSize: "0.9rem" }}>{tr("checkout.iHaveAGstinBusiness")}</span>
              </label>
              {b2b && (
                <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label htmlFor="shipping-gstin" style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>
                      GSTIN <span style={{ color: "var(--color-error)" }}>*</span>
                    </label>
                    <input id="shipping-gstin" name="gstin" value={gstin} onChange={(e) => setGstin(e.target.value.toUpperCase())}
                      placeholder={tr("checkout.22aaaaa0000a1z5")} maxLength={15}
                      style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none", textTransform: "uppercase" }} />
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label htmlFor="shipping-legal-name" style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>{tr("checkout.registeredLegalName")}{" "}<span style={{ color: "var(--color-error)" }}>*</span>
                    </label>
                    <input id="shipping-legal-name" name="buyerLegalName" value={buyerLegalName} onChange={(e) => setBuyerLegalName(e.target.value)}
                      placeholder={tr("checkout.asRegisteredWithGst")}
                      style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none" }} />
                  </div>
                  <p style={{ margin: 0, fontSize: "0.78rem", color: "var(--color-text-secondary)" }}>{tr("checkout.yourInvoiceWillBeIssued")}</p>

                  {/*
                    Section 10(1)(b). Declared, never guessed: a delivery address in another
                    state is not by itself a bill-to / ship-to, and reading it as one would move
                    the place of supply - and with it the whole tax split - on an ordinary order
                    that simply ships somewhere else.
                  */}
                  <div style={{ paddingTop: "0.75rem", borderTop: "1px dashed var(--color-border-light)" }}>
                    <label style={{ display: "flex", alignItems: "flex-start", gap: "0.5rem", cursor: "pointer" }}>
                      <input type="checkbox" checked={billToShipTo}
                        onChange={(e) => setBillToShipTo(e.target.checked)} style={{ marginTop: "0.2rem" }} />
                      <span>
                        <span style={{ fontWeight: 600, fontSize: "0.9rem" }}>{tr("checkout.deliverToSomeoneElse")}</span>
                        <span style={{ display: "block", fontSize: "0.78rem", color: "var(--color-text-secondary)" }}>{tr("checkout.billThisToMyBusiness")}</span>
                      </span>
                    </label>
                  </div>

                  {billToShipTo && (
                    <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                        <label htmlFor="consignee-name" style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>{tr("checkout.whoIsReceivingTheGoods")}{" "}<span style={{ color: "var(--color-error)" }}>*</span>
                        </label>
                        <input id="consignee-name" name="consigneeName" value={consigneeName}
                          onChange={(e) => setConsigneeName(e.target.value)}
                          placeholder={tr("checkout.nameOfTheConsignee")}
                          style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none" }} />
                      </div>
                      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                        <label htmlFor="consignee-address" style={{ fontSize: "0.85rem", fontWeight: 500, color: "var(--color-text-secondary)" }}>{tr("checkout.theirAddress")}</label>
                        <textarea id="consignee-address" name="consigneeAddress" value={consigneeAddress}
                          onChange={(e) => setConsigneeAddress(e.target.value)} rows={2}
                          placeholder={tr("checkout.leaveBlankToUseThe")}
                          style={{ padding: "0.6rem 0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", fontSize: "0.9rem", outline: "none", resize: "vertical" }} />
                      </div>
                      {/*
                        Said plainly, because it is the part that surprises people: the tax
                        follows the buyer's registered state, not where the parcel goes.
                      */}
                      <p style={{ margin: 0, fontSize: "0.78rem", color: "var(--color-text-secondary)" }}>{tr("checkout.underSection101B")}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        );

      case 1:
        return (
          <div className="checkout-section" style={{
            padding: "1.25rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-md)", background: "var(--color-surface)"
          }}>
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>{tr("checkout.howWouldYouLikeTo")}</h2>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              {[
                { value: "CARD", label: tr("checkout.creditDebitCard"), desc: tr("checkout.visaMastercardAndRupay") },
                { value: "UPI", label: "UPI", desc: tr("checkout.googlePayPhonepePaytmOr") },
                { value: "NET_BANKING", label: tr("footer.netBanking"), desc: tr("checkout.allMajorBanks") },
                { value: "WALLET", label: tr("checkout.wallet"), desc: tr("checkout.paytmAmazonPayMobikwik") },
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

            <div style={{ marginTop: "0.75rem", padding: "0.75rem", border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
                <input type="checkbox" checked={giftWrap} onChange={(e) => setGiftWrap(e.target.checked)} />
                <span style={{ fontWeight: 500, fontSize: "0.9rem" }}>{tr("checkout.giftWrap49")}</span>
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
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>{tr("checkout.checkYourOrder")}</h2>
            <div style={{ marginBottom: "1rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>{tr("checkout.deliveringTo")}</h3>
              <div style={{ fontSize: "0.9rem", color: "var(--color-text-secondary)" }}>
                {shipping.fullName}, {shipping.phone}<br />
                {shipping.line1 || shipping.street}{shipping.line2 ? `, ${shipping.line2}` : ""}, {shipping.city}, {shipping.state} - {shipping.pincode}{shipping.country && shipping.country !== "India" ? `, ${shipping.country}` : ""}
              </div>
            </div>
            <div style={{ marginBottom: "1rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>{tr("checkout.paymentMethod")}</h3>
              <div style={{ fontSize: "0.9rem", color: "var(--color-text-secondary)" }}>{PAYMENT_LABELS[paymentMethod] || paymentMethod}</div>
            </div>
            <div>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.5rem" }}>{tr("checkout.itemsCount", { count: cartItems.length })}</h3>
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
                      <div style={{ color: "var(--color-text-secondary)" }}>{variantLabel(item) ? `${variantLabel(item)} · ` : ""}{tr("checkout.qty")}{" "}{item.quantity || 1}</div>
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
            <h2 style={{ fontSize: "1.3rem", fontWeight: 700, marginBottom: "0.5rem" }}>{tr("checkout.oneLastLook")}</h2>
            <p style={{ color: "var(--color-text-secondary)", marginBottom: "1.5rem" }}>{tr("checkout.yourTotalIs")}{" "}<strong>&#8377;{total.toFixed(2)}</strong>{tr("checkout.includingGstAndDelivery")}</p>
            <p style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginBottom: "0.5rem" }}>{tr("checkout.youCanCancelFreeOf")}</p>
            {giftWrap && <p style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>{tr("checkout.includesGiftWrap")}</p>}
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="checkout-container" style={{ maxWidth: 900, margin: "0 auto", padding: "1.5rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1rem" }}>{tr("checkout.title")}</h1>
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
            }}>{tr("checkout.back")}</button>

            {step < STEPS.length - 1 ? (
              <button onClick={nextStep} style={{
                padding: "0.6rem 1.25rem", border: "none", borderRadius: "var(--radius-sm)",
                background: "var(--color-primary)", color: "#fff", cursor: "pointer", fontSize: "0.9rem", fontWeight: 600
              }}>{tr("checkout.continue")}</button>
            ) : (
              <button onClick={handlePlaceOrder} disabled={placing}
                style={{
                  padding: "0.75rem 2rem", border: "none", borderRadius: "var(--radius-sm)",
                  background: placing ? "var(--color-gray-300)" : "var(--color-primary)", color: "#fff",
                  cursor: placing ? "not-allowed" : "pointer", fontSize: "1rem", fontWeight: 700
                }}>
                {placing ? tr("checkout.placingYourOrder") : `Place order \u00B7 \u20B9${total.toFixed(2)}`}
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
            <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem" }}>{tr("checkout.orderSummary")}</h2>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>{tr("cart.subtotal")}</span>
              <span>&#8377;{subtotal.toFixed(2)}</span>
            </div>
            {discount > 0 && (
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
                <span style={{ color: "#0E5C5C" }}>{tr("cart.discount")}</span>
                <span style={{ color: "#0E5C5C" }}>-&#8377;{discount.toFixed(2)}</span>
              </div>
            )}
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>{tr("checkout.subtotalAfterDiscount")}</span>
              <span>&#8377;{(subtotal - discount).toFixed(2)}</span>
            </div>
            {/* GST, itemised. A basket can carry four different rates at once, so a single
                figure is nothing the customer could check against their invoice. */}
            {bill?.lines?.length > 0 && (
              <div style={{
                margin: "0.75rem 0", padding: "0.6rem", borderRadius: 6,
                background: "var(--color-bg, #f9fafb)", border: "1px solid var(--color-border)",
              }}>
                <div style={{ fontSize: "0.78rem", fontWeight: 600, marginBottom: "0.4rem" }}>{tr("checkout.gstIncludedInThesePrices")}</div>
                {bill.lines.map((l) => (
                  <div key={l.productId} style={{ fontSize: "0.75rem", marginBottom: "0.45rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", gap: "0.5rem" }}>
                      <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {l.description}{l.quantity > 1 ? ` x${l.quantity}` : ""}
                      </span>
                      <span style={{ whiteSpace: "nowrap" }}>&#8377;{l.taxAmount.toFixed(2)}</span>
                    </div>
                    <div style={{ color: "var(--color-text-secondary)" }}>{tr("checkout.hsn")}{" "}{l.hsnCode}· ₹{l.taxableValue.toFixed(2)}@ {l.gstRate}%
                                            {l.igstAmount > 0
                        ? ` (IGST ${l.igstRate}%)`
                        : ` (CGST ${l.cgstRate}% + SGST ${l.sgstRate}%)`}
                      {" → "}₹{l.lineTotal.toFixed(2)}
                    </div>
                  </div>
                ))}
                <div style={{
                  display: "flex", justifyContent: "space-between", fontSize: "0.78rem",
                  fontWeight: 600, borderTop: "1px solid var(--color-border)", paddingTop: "0.4rem",
                }}>
                  <span>{tr("checkout.totalGstIncluded")}</span>
                  <span>&#8377;{tax !== null ? tax.toFixed(2) : bill.totalTax.toFixed(2)}</span>
                </div>
              </div>
            )}

            {!bill?.lines?.length && (
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>{tr("checkout.gstIncluded")}</span>
                <span>
                  {billLoading ? "calculating…"
                    : taxKnown ? `₹${tax.toFixed(2)}`
                    : "enter delivery address"}
                </span>
              </div>
            )}

            {bill && bill.canProceed === false && bill.blocked?.length > 0 && (
              <div style={{
                margin: "0.5rem 0", padding: "0.55rem", borderRadius: 6,
                background: "#fef2f2", border: "1px solid #fecaca", color: "#b91c1c",
                fontSize: "0.75rem",
              }}>
                <strong>{tr("checkout.thisOrderCannotBePlaced")}</strong>
                {bill.blocked.map((b) => (
                  <div key={b.productId} style={{ marginTop: 4 }}>{b.description}: {b.reason}</div>
                ))}
              </div>
            )}
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>{tr("cart.delivery")}</span>
              <span>{delivery === 0 ? <span style={{ color: "#0E5C5C" }}>{tr("cart.free")}</span> : `\u20B9${delivery}`}</span>
            </div>
            {giftCharge > 0 && (
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.5rem", fontSize: "0.9rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>{tr("checkout.giftWrap")}</span>
                <span>&#8377;{giftCharge}</span>
              </div>
            )}
            <div style={{
              display: "flex", justifyContent: "space-between", marginTop: "0.5rem",
              paddingTop: "0.75rem", borderTop: "1px solid var(--color-border)",
              fontWeight: 700, fontSize: "1.05rem"
            }}>
              <span>{tr("checkout.total")}</span>
              <span>&#8377;{total.toFixed(2)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Checkout;
